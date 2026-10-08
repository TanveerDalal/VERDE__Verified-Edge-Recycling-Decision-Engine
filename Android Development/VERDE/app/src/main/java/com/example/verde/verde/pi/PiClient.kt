package com.example.verde.pi

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.verde.data.WasteClass
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Everything that talks to the Raspberry Pi over Wi-Fi (the team's server.py).
 *
 *   GET  /health     → {"status": "ok"}           is the Pi there?
 *   GET  /stream     → live MJPEG preview
 *   POST /scan       → {"label": "Plastic", "confidence": 0.93, ...}
 *   GET  /photo.jpg  → photo of the last scan (optional, for the Result screen)
 *
 * Network work can't run on the main thread (it would freeze the screen), so every request
 * runs in the background and its answer is handed back on the main thread.
 */
class PiClient(private val address: String) {

    val streamUrl = "$address/stream"

    private val mainThread = Handler(Looper.getMainLooper())

    /** No answer → OFFLINE · "camera": false → NO_CAMERA · otherwise → READY */
    fun checkStatus(onResult: (PiState) -> Unit) {
        inBackground({
            val reply = JSONObject(read(get("/health")))
            if (reply.optBoolean("camera", true)) PiState.READY else PiState.NO_CAMERA
        }) { result -> onResult(result.getOrDefault(PiState.OFFLINE)) }
    }

    /**
     * Asks the Pi to take a photo and identify it. Downloads the photo into [photoFile]
     * (if the Pi offers it). Answers null if the scan failed.
     */
    fun scan(photoFile: File, onResult: (Classifier.Answer?) -> Unit) {
        inBackground({
            photoFile.delete()        // never show an old photo with a new answer

            val reply = JSONObject(read(post("/scan")))
            val answer = Classifier.Answer(
                wasteClass = WasteClass.fromLabel(reply.getString("label")),
                confidence = (reply.getDouble("confidence") * 100).roundToInt()   // 0.93 → 93
            )
            runCatching { download("/photo.jpg", photoFile) }   // no photo → Result shows the placeholder
            answer
        }) { result -> onResult(result.getOrNull()) }
    }

    // ───────────── Small helpers ─────────────

    private fun get(path: String) = open(path)

    private fun post(path: String) = open(path).apply {
        requestMethod = "POST"
        readTimeout = 15_000          // capture + (later) the AI model take a while
    }

    private fun read(connection: HttpURLConnection): String =
        connection.inputStream.bufferedReader().use { it.readText() }

    private fun download(path: String, file: File) {
        get(path).inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    /** A connection that gives up quickly if the Pi isn't there. */
    private fun open(path: String) = (URL(address + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 2000   // 2 s to find the Pi
        readTimeout = 5000      // 5 s for it to reply
    }

    /** Runs [work] in the background, then hands the result to [onDone] on the main thread. */
    private fun <T> inBackground(work: () -> T, onDone: (Result<T>) -> Unit) {
        Thread {
            val result = runCatching(work)
            result.exceptionOrNull()?.let { Log.d("Verde", "Pi request failed: ${it.message}") }
            mainThread.post { onDone(result) }
        }.start()
    }
}
