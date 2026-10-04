package com.verde.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

// Model input size used inside the app (same as the Pi's camera.py and your preprocessing)
const val MODEL_INPUT_SIZE = 224

// The 9 classes the AI will predict later. Must match the dataset's folder names exactly.
val WASTE_CLASSES = listOf(
    "Cardboard", "Food Organics", "Glass", "Metal", "Miscellaneous Trash",
    "Paper", "Plastic", "Textile Trash", "Vegetation"
)

data class PhoneCaptureResult(
    val image: Bitmap,          // 224×224, what the model sees inside the app
    val photoWidth: Int,        // full photo saved to VERDE_captures
    val photoHeight: Int,
    val file: File,
    val capturePath: String?,   // where the full photo was saved, or null if saving failed
    val captureMs: Long,
    val preprocessMs: Long,
    val saveMs: Long
) {
    val totalMs: Long get() = captureMs + preprocessMs + saveMs
}

// Keep the centre square of the photo (same as the Pi)
fun centreCropSquare(source: Bitmap): Bitmap {
    val side = minOf(source.width, source.height)
    val x = (source.width - side) / 2
    val y = (source.height - side) / 2
    return Bitmap.createBitmap(source, x, y, side, side)
}

// Resize a square image to size × size
fun resizeTo(source: Bitmap, size: Int): Bitmap =
    Bitmap.createScaledBitmap(source, size, size, true)

// Turn the image upright (the camera sensor is mounted sideways inside the phone)
fun rotate(source: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return source
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

// Private copy of the 224×224 model input (part of the timed pipeline)
fun saveScan(context: Context, bitmap: Bitmap): File {
    val dir = File(context.filesDir, "scans").apply { mkdirs() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

// Save the full-quality photo to Pictures/VERDE_captures/ for preprocessing in Python later
fun saveCapture(context: Context, bitmap: Bitmap): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null // Android 10+ only
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.UK).format(Date())
    val fileName = "phone_$stamp.jpg"
    val folder = "Pictures/VERDE_captures"

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, folder)
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: return null
    context.contentResolver.openOutputStream(uri)?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
    } ?: return null
    return "$folder/$fileName"
}

// Take a photo, run the timed 224×224 pipeline, then save the full photo
fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onResult: (PhoneCaptureResult) -> Unit,
    onFailure: (String) -> Unit
) {
    val start = System.nanoTime()
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val captured = System.nanoTime()
            val rotation = image.imageInfo.rotationDegrees
            val full = image.toBitmap()
            image.close()

            // Timed pipeline (benchmark): crop → resize → rotate
            val square = centreCropSquare(full)
            val small = resizeTo(square, MODEL_INPUT_SIZE)
            val upright = rotate(small, rotation)
            val processed = System.nanoTime()

            val file = saveScan(context, upright)
            val saved = System.nanoTime()

            // Full-quality photo, saved after timing stops so the benchmark isn't affected.
            // No crop or resize: preprocessing happens later in your Python notebook.
            val fullUpright = rotate(full, rotation)
            val capturePath = saveCapture(context, fullUpright)

            onResult(
                PhoneCaptureResult(
                    image = upright,
                    photoWidth = fullUpright.width,
                    photoHeight = fullUpright.height,
                    file = file,
                    capturePath = capturePath,
                    captureMs = (captured - start) / 1_000_000,
                    preprocessMs = (processed - captured) / 1_000_000,
                    saveMs = (saved - processed) / 1_000_000
                )
            )
        }

        override fun onError(exception: ImageCaptureException) {
            onFailure(exception.message ?: "Capture failed")
        }
    })
}