package com.verde.app.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.verde.app.camera.MODEL_INPUT_SIZE
import com.verde.app.camera.PhoneCaptureResult
import com.verde.app.camera.centreCropSquare
import com.verde.app.camera.resizeTo
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Image recognition ON THE PHONE: runs model_b0.pte with ExecuTorch (XNNPACK, Arm CPU).
 * Model: verde_effnet_b0_T3_s7 (EfficientNet-B0, 8 classes, validation accuracy 94.1%).
 * Same preprocessing as training: 224x224 RGB, ImageNet mean/std.
 * Used for phone-camera photos AND for the Pi camera's picture (see HomeActivity).
 */
object Classifier {

    private const val MODEL_ASSET = "model_b0_3_t3_seed42.pte"
    private const val UNCERTAIN_BELOW = 60            // "not sure" below 60%, as in the real-photo check notebook
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    // Same order as the model's class_names.json
    private val CLASSES = listOf(
        WasteClass.FOOD_GARDEN, WasteClass.GENERAL, WasteClass.GLASS, WasteClass.METAL,
        WasteClass.PAPER_CARD, WasteClass.PLASTIC, WasteClass.SPECIAL, WasteClass.TEXTILE
    )

    @Volatile private var module: Module? = null

    /** Loads the model once (a second or two the first time). Call it early, off the main thread. */
    fun warmUp(context: Context) { module(context) }

    private fun module(context: Context): Module =
        module ?: synchronized(this) {
            module ?: Module.load(modelFile(context).absolutePath).also { module = it }
        }

    /**
     * Module.load needs a real file, so copy the model out of the app's assets.
     * It is copied again whenever the app is updated, so a new model_b0.pte is picked up
     * after ▶ Run without uninstalling.
     */
    private fun modelFile(context: Context): File {
        val file = File(context.filesDir, MODEL_ASSET)
        val appUpdated = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (!file.exists() || file.length() == 0L || file.lastModified() < appUpdated) {
            val tmp = File(context.filesDir, "$MODEL_ASSET.tmp")
            context.assets.open(MODEL_ASSET).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(file)
        }
        return file
    }

    /** Runs the model on a phone photo (already cropped and resized to 224x224). */
    fun classify(context: Context, photo: PhoneCaptureResult): ScanAnswer =
        classifySmall(context, photo.image, photo.totalMs, "phone camera")

    /** Runs the model on any picture (e.g. the Pi camera's frame): centre square → 224x224. */
    fun classify(context: Context, picture: Bitmap, extraMs: Long = 0, source: String = "Pi camera"): ScanAnswer {
        val start = System.nanoTime()
        val small = resizeTo(centreCropSquare(picture), MODEL_INPUT_SIZE)
        val prepMs = (System.nanoTime() - start) / 1_000_000
        return classifySmall(context, small, extraMs + prepMs, source)
    }

    private fun classifySmall(context: Context, image: Bitmap, beforeMs: Long, source: String): ScanAnswer {
        val model = module(context)
        val input = toTensor(image)

        LocalLlama.stopNow()                                   // scans first: stop any Llama answer
        EngineLock.lock.lock()
        val start = System.nanoTime()
        val logits = try {
            model.forward(EValue.from(input))[0].toTensor().dataAsFloatArray
        } finally {
            EngineLock.lock.unlock()
        }
        val inferenceMs = (System.nanoTime() - start) / 1_000_000

        val probs = softmax(logits)
        val best = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = (probs[best] * 100).roundToInt()

        val top3 = probs.indices.sortedByDescending { probs[it] }.take(3)
            .joinToString { "${CLASSES[it].name} ${(probs[it] * 100).roundToInt()}%" }
        Log.d("VERDE", "[phone-only] $source: $top3 | inference $inferenceMs ms")

        return ScanAnswer(
            wasteClass = CLASSES[best],
            confidence = confidence,
            uncertain = confidence < UNCERTAIN_BELOW,
            timeMs = beforeMs + inferenceMs,           // capture/prep + inference
            guesses = probs.indices.sortedByDescending { probs[it] }            // all 8, for the chart
                .map { Guess(CLASSES[it], (probs[it] * 100).roundToInt()) },
            inferenceMs = inferenceMs
        )
    }

    /** Bitmap -> normalised float tensor, shape (1, 3, 224, 224), channels first. */
    private fun toTensor(bitmap: Bitmap): Tensor {
        val size = MODEL_INPUT_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val data = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]            // red
            data[plane + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]     // green
            data[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]         // blue
        }
        return Tensor.fromBlob(data, longArrayOf(1, 3, size.toLong(), size.toLong()))
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { exp(logits[it] - max) }
        val sum = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }
}