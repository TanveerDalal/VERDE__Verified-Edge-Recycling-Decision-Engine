package com.verde.app.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmGenerationConfig
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File

/**
 * Llama 3.2 1B running INSIDE Verde on the phone, with ExecuTorch (XNNPACK, Arm CPU).
 * It uses the same files as the ExecuTorch Llama demo app:
 *   /data/local/tmp/llama/<something>.pte   and   /data/local/tmp/llama/tokenizer.model
 * (copied there with `adb push`). No model files → every function answers null,
 * and Verde falls back to the Pi's Llama or the built-in answers.
 */
object LocalLlama {

    private const val TAG = "VERDE-Llama"

    /**
     * true  = DIRECT: your question goes straight to Llama, like the ExecuTorch demo app
     *         (no Verde facts, no length limit). Longer answers, but Llama may guess.
     * false = GROUNDED: Verde gives Llama its checked UK fact and asks for 3 short sentences.
     */
    const val DIRECT_MODE = false

    private const val TEMPERATURE = 0.0f                    // 0 = always the most likely words: steady, factual
    private const val SEQ_LEN = 1024                         // the most this model allows (prompt + answer)
    private val FOLDERS = listOf("/data/local/tmp/llama")    // same place as the demo app

    private const val SYSTEM =
        "You are Verde, a friendly recycling assistant for UK households. " +
        "Give practical, correct advice in plain English. Keep answers short. " +
        "If rules depend on the local council, say so."

    // Direct mode: the same kind of plain system prompt the demo app uses
    private const val SYSTEM_DIRECT = "You are a helpful assistant."

    private val lock = Mutex()                               // one answer at a time
    @Volatile private var module: LlmModule? = null

    /** A scan needs the engine: stop the Llama answer in progress (it returns what it has so far). */
    fun stopNow() {
        if (EngineLock.lock.isLocked) runCatching { module?.stop() }
    }

    /** Is a Llama model on this phone? */
    fun isAvailable(context: Context): Boolean = findFiles(context) != null

    /** Free question → Llama's answer (Ask tab), grounded on Verde's checked fact if there is one. */
    suspend fun ask(context: Context, question: String): String? =
        if (DIRECT_MODE) generate(context, question.trim(), SYSTEM_DIRECT)       // straight to Llama
        else generate(context, LlamaPrompts.ask(question, WasteKnowledge.fact(context, question)))

    /** Classified item → BIN/STEP/WARNING/WHY text (parsed by LlamaClient). */
    suspend fun adviceText(context: Context, label: String, confidence: Int, fact: String?): String? =
        generate(context, LlamaPrompts.advice(label, confidence, fact))

    // ── ExecuTorch ───────────────────────────────────────────────────────────────

    private suspend fun generate(context: Context, user: String, system: String = SYSTEM): String? =
        withContext(Dispatchers.IO) {                        // generate() blocks: never on the main thread
            lock.withLock {
                try {
                    val llm = module(context) ?: return@withLock null
                    val prompt = chatPrompt(user, system)
                    val text = StringBuilder()
                    val start = System.nanoTime()

                    // Each question is new: forget the previous one, or Llama's memory fills up
                    llm.resetContext()
                    val config = LlmGenerationConfig.create()
                        .seqLen(SEQ_LEN)                       // long enough for a full answer
                        .temperature(TEMPERATURE)
                        .echo(false)                           // don't repeat the prompt back
                        .build()

                    EngineLock.lock.lock()                     // never at the same time as a scan
                    try {
                        llm.generate(prompt, config, object : LlmCallback {
                            override fun onResult(token: String) { text.append(token) }   // one piece at a time
                        })
                    } finally {
                        EngineLock.lock.unlock()
                    }

                    Log.d(TAG, "answer in ${(System.nanoTime() - start) / 1_000_000} ms")
                    clean(text.toString(), prompt).ifEmpty { null }
                } catch (e: Throwable) {
                    Log.e(TAG, "Llama failed", e)
                    null
                }
            }
        }

    /** Loads the model once (a few seconds the first time), then keeps it in memory. */
    private fun module(context: Context): LlmModule? {
        module?.let { return it }
        val (model, tokenizer) = findFiles(context) ?: return null
        Log.d(TAG, "loading ${model.name}")
        return LlmModule(model.absolutePath, tokenizer.absolutePath, TEMPERATURE).also { module = it }
    }

    /** Finds a .pte (name starting with "llama") and tokenizer.model / tokenizer.bin. */
    private fun findFiles(context: Context): Pair<File, File>? {
        val folders = FOLDERS.map(::File) + File(context.filesDir, "llama")
        for (folder in folders) {
            val files = folder.listFiles() ?: continue
            val model = files.firstOrNull { it.name.startsWith("llama", true) && it.name.endsWith(".pte") }
            val tokenizer = files.firstOrNull { it.name.startsWith("tokenizer") }
            if (model != null && tokenizer != null && model.canRead()) return model to tokenizer
        }
        return null
    }

    /** Llama 3 chat format (the same format the demo app uses for LLAMA_3). */
    private fun chatPrompt(user: String, system: String) =
        "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>" +
        "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
        "<|start_header_id|>assistant<|end_header_id|>\n\n"

    /** Keeps only Llama's answer: drops an echoed prompt, special tokens, **bold** marks and outer quotes. */
    private fun clean(output: String, prompt: String): String {
        var text = output
        if (text.startsWith(prompt)) text = text.removePrefix(prompt)
        else if ("assistant<|end_header_id|>" in text) text = text.substringAfterLast("assistant<|end_header_id|>")
        text = text.replace(Regex("<\\|[^|]*\\|>"), "")      // <|eot_id|> etc.
            .replace("**", "")                                   // chat bubbles show plain text
            .trim()
        if (text.length > 1 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1).trim()
        return text
    }
}

/**
 * The questions we ask Llama. A 1B model can invent wrong rules, so when Verde has a
 * checked fact we give it to Llama and ask it to explain that fact in its own words.
 */
object LlamaPrompts {
    private fun factLine(fact: String?) =
        if (fact == null) "" else "Background knowledge (correct, use it, never contradict it): $fact\n\n"

    fun ask(question: String, fact: String?) =
        factLine(fact) + "User's question: ${question.trim()}\n\n" +
        "Reply directly to the user as Verde, using \"you\", in at most 3 short friendly sentences. " +
        "Just give the advice. Do not mention background knowledge, facts or these instructions."

    fun advice(label: String, confidence: Int, fact: String?) =
        factLine(fact) +
        "A camera identified an item as: $label ($confidence% sure).\n" +
        "Reply in exactly this format, nothing else:\n" +
        "BIN: <which UK bin it goes in>\n" +
        "STEP: <first thing to do>\n" +
        "STEP: <second thing to do>\n" +
        "STEP: <third thing to do>\n" +
        "WARNING: <a safety danger only (fire, sharp edges, chemicals), otherwise write none>\n" +
        "WHY: <one sentence on why this matters for the environment>"
}
