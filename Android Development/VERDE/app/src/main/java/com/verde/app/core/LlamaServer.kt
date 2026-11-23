package com.verde.app.core

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Lets the WhatsApp server use the Llama that runs INSIDE Verde on this phone.
 *
 *   WhatsApp → Meta → PC server ──POST /ask──► this phone :5001 ──► LocalLlama (ExecuTorch) ──► answer
 *
 * Same API as the Pi's llama_server.py, so the PC server doesn't care which one it talks to:
 *   GET  /health  → {"status":"ok", "engine":"ExecuTorch", ...}
 *   POST /ask     {"question": "..."}  →  {"answer": "...", "total_ms": 3200, "device": "..."}
 *   POST /classify  (a JPEG photo)     →  the image model's answer (WhatsApp photos)
 *
 * Reach it from the PC over USB with:  adb forward tcp:5001 tcp:5001  → http://127.0.0.1:5001
 * It runs while Verde is open (started from the Ask tab).
 */
object LlamaServer {

    const val PORT = 5001
    private const val TAG = "VERDE-LlamaServer"
    private val DEVICE = "Android phone (${Build.MODEL})"

    @Volatile private var started = false

    /** Starts the server once; calling it again does nothing. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        thread(name = "verde-llama-server", isDaemon = true) {
            try {
                ServerSocket(PORT).use { server ->
                    Log.d(TAG, "Llama server listening on port $PORT")
                    while (true) {
                        val socket = server.accept()
                        thread(isDaemon = true) {
                            try { socket.use { handle(app, it) } }
                            catch (e: Throwable) { Log.e(TAG, "request failed", e) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Llama server stopped", e)
                started = false
            }
        }
    }

    private fun handle(context: Context, socket: Socket) {
        socket.soTimeout = 10_000                                  // reading the request only
        val input = socket.getInputStream()

        // 1. Request line, e.g. "POST /ask HTTP/1.1"
        val parts = (readLine(input) ?: return).split(" ")
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty()

        // 2. Headers: we only need Content-Length
        var length = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            if (line.startsWith("content-length:", ignoreCase = true)) {
                length = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }

        // 3. Body (JSON)
        val limit = if (path.startsWith("/classify")) 20_000_000 else 100_000   // photos are bigger
        val bytes = ByteArray(length.coerceIn(0, limit))
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        val body = String(bytes, 0, read, Charsets.UTF_8)

        val (status, json) = try { when {
            method == "GET" && path.startsWith("/health") -> 200 to JSONObject()
                .put("status", "ok").put("engine", "ExecuTorch").put("model", "Llama 3.2 1B")
                .put("device", DEVICE).put("llama_ready", LocalLlama.isAvailable(context))
            method == "POST" && path.startsWith("/ask") -> ask(context, body)
            method == "POST" && path.startsWith("/classify") -> classify(context, bytes.copyOf(read))
            else -> 404 to JSONObject().put("error", "not_found")
        } } catch (e: Throwable) {                                 // always answer, never hang
            Log.e(TAG, "$path failed", e)
            500 to JSONObject().put("error", e.javaClass.simpleName)
        }
        reply(socket, status, json)
    }

    /** Question → Llama's answer on this phone. */
    private fun ask(context: Context, body: String): Pair<Int, JSONObject> {
        val question = runCatching { JSONObject(body).optString("question") }.getOrDefault("").trim()
        if (question.isEmpty()) return 400 to JSONObject().put("error", "no_question")

        Safety.reply(question)?.let {                              // never answer these with recycling advice
            return 200 to JSONObject().put("answer", it).put("safety", true)
        }

        Log.d(TAG, "WhatsApp question: ${question.take(60)}")
        val start = System.currentTimeMillis()
        val answer = runBlocking { LocalLlama.ask(context, question) }
            ?: return 503 to JSONObject().put("error", "llama_unavailable")
        Log.d(TAG, "answered WhatsApp question in ${System.currentTimeMillis() - start} ms")
        return 200 to JSONObject()
            .put("answer", answer)
            .put("total_ms", System.currentTimeMillis() - start)
            .put("engine", "ExecuTorch")
            .put("model", "Llama 3.2 1B")
            .put("device", DEVICE)
    }

    /** A WhatsApp photo → the same ExecuTorch image model as the app's camera. */
    private fun classify(context: Context, photo: ByteArray): Pair<Int, JSONObject> {
        if (photo.isEmpty()) return 400 to JSONObject().put("error", "no_photo")
        // Read it at about 1–2 thousand pixels, not full size (saves memory)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo, 0, photo.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        val picture = BitmapFactory.decodeByteArray(photo, 0, photo.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return 400 to JSONObject().put("error", "not_an_image")

        val answer = Classifier.classify(context, picture, 0, "WhatsApp")
        val item = answer.wasteClass
        Log.d(TAG, "WhatsApp photo: ${item.name} ${answer.confidence}% in ${answer.inferenceMs} ms")
        val guesses = org.json.JSONArray()
        answer.guesses.take(3).forEach {
            guesses.put(JSONObject().put("label", context.getString(it.wasteClass.label)).put("percent", it.percent))
        }
        return 200 to JSONObject()
            .put("class", item.name)
            .put("label", context.getString(item.label))
            .put("bin", context.getString(item.bin.label))
            .put("advice", context.getString(item.advice))
            .put("confidence", answer.confidence)
            .put("uncertain", answer.uncertain)
            .put("guesses", guesses)
            .put("inference_ms", answer.inferenceMs)
            .put("engine", "ExecuTorch")
            .put("model", "EfficientNet-B0")
            .put("device", DEVICE)
    }

    private fun reply(socket: Socket, status: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(bytes)
            flush()
        }
    }

    /** Reads one line ending in \n (drops \r). Null if the connection closed. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
    }
}
