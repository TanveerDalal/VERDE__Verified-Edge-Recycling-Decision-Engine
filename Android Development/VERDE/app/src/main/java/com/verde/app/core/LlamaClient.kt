package com.verde.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Verde's Llama, in this order:
 *   1. on this phone (LocalLlama: ExecuTorch, same model as the Llama demo app)
 *   2. through Verde's server over the internet (WhatsAppConfig.SERVER_URL + /ask), which asks
 *      the phone or Pi that runs Llama: so phones WITHOUT Llama still get Llama answers
 *   3. on the Raspberry Pi on the same Wi-Fi (llama-pi/llama_server.py, port 5001, ExecuTorch)
 * Every function answers null if neither works, so screens can fall back to built-in text.
 */
object LlamaClient {

    private const val PORT = 5001

    /** Llama's advice for one item (same fields the Result screen shows). */
    data class Advice(
        val steps: List<String>,
        val warnings: List<String>,
        val whyItMatters: String?,
        val timeMs: Long?
    )

    // Llama on a Pi is slow (a few words per second), so wait up to 2 minutes
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Free question (Ask tab) → Llama's answer, or null. */
    suspend fun ask(context: Context, question: String): String? {
        Safety.reply(question)?.let { return it }                      // someone may be in danger
        if (PiSettings.llamaOnPhone(context)) {                         // switch in the Ask tab: phone…
            val answer = LocalLlama.ask(context, question) ?: return null
            return "$answer\n\n🦙 Llama 3.2 1B · ExecuTorch · on this phone"
        }
        // …or the Pi (Llama 3.1 8B on your Wi-Fi)
        val body = JSONObject().put("question", question).put("fact", WasteKnowledge.fact(context, question))
        val reply = post(context, "/ask", body) ?: return null
        val answer = reply.optString("answer").trim().ifEmpty { return null }
        return "$answer\n\n🦙 " + reply.optString("device", "Raspberry Pi") +
                " · " + reply.optString("engine", "ExecuTorch") + " (on your Wi-Fi)"
    }

    /**
     * Asks the Llama behind Verde's server (internet). Null if the server or its Llama is off.
     * The answer ends with a line saying where Llama ran, as proof for the demo.
     */
    private suspend fun askServer(question: String): String? = withContext(Dispatchers.IO) {
        val base = WhatsAppConfig.SERVER_URL.trimEnd('/')
        if (!base.startsWith("https://") || "your-link" in base) return@withContext null
        try {
            val request = Request.Builder()
                .url("$base/ask")
                .header("X-App-Key", WhatsAppConfig.APP_KEY)
                .header("ngrok-skip-browser-warning", "true")
                .post(JSONObject().put("question", question).toString()
                    .toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                val answer = json.optString("answer").trim()
                when {
                    answer.isEmpty() -> null
                    json.optBoolean("safety") -> answer
                    else -> "$answer\n\n🦙 Llama 3.2 1B · " + json.optString("engine", "ExecuTorch") +
                            " · " + json.optString("device", "Verde server") + " (via Verde server)"
                }
            }
        } catch (e: Exception) {
            null                                       // no internet, server off, timeout…
        }
    }

    /** Classified item → Llama's steps, warnings and "why it matters", or null. */
    suspend fun advice(context: Context, label: String, confidence: Int, fact: String? = null): Advice? {
        val start = System.currentTimeMillis()                          // 1. on the phone
        LocalLlama.adviceText(context, label, confidence, fact)?.let { text ->
            parseAdvice(text, System.currentTimeMillis() - start)?.let { return it }
        }
        val body = JSONObject().put("label", label).put("confidence", confidence).put("fact", fact)
        val reply = post(context, "/advice", body) ?: return null
        val steps = reply.optJSONArray("steps").toStrings()
        if (steps.isEmpty()) return null
        return Advice(
            steps = steps,
            warnings = reply.optJSONArray("warnings").toStrings(),
            whyItMatters = reply.optString("why_it_matters").takeIf { it.isNotBlank() && it != "null" },
            timeMs = if (reply.has("total_ms")) reply.getLong("total_ms") else null
        )
    }

    /** BIN:/STEP:/WARNING:/WHY: lines → Advice. A 1B model sometimes drifts, so be forgiving. */
    private fun parseAdvice(text: String, timeMs: Long): Advice? {
        val steps = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var why: String? = null
        for (line in text.lines()) {
            val key = line.substringBefore(":", "").trim().uppercase()
            val value = line.substringAfter(":", "").trim(' ', '-', '*')
            if (value.isEmpty()) continue
            when {
                key.startsWith("STEP") -> steps += value
                key == "WARNING" && value.lowercase().trimEnd('.') !in setOf("none", "no", "n/a") -> warnings += value
                key == "WHY" -> why = value
            }
        }
        if (steps.isEmpty() && text.isNotBlank()) steps += text.trim()   // no format → one step
        return if (steps.isEmpty()) null else Advice(steps.take(5), warnings, why, timeMs)
    }

    private suspend fun post(context: Context, path: String, body: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://${PiSettings.address(context)}:$PORT$path")
                    .header("X-Token", PiSettings.llamaToken(context))
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) JSONObject(response.body?.string().orEmpty()) else null
                }
            } catch (e: Exception) {
                null                                   // Pi off, wrong Wi-Fi, timeout…
            }
        }

    private fun JSONArray?.toStrings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }.filter { it.isNotBlank() }
}