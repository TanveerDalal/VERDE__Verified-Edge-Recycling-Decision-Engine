package com.example.verde.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * The phone's camera (CameraX): live picture, photos, torch, and switching
 * between the back and front camera.
 * Tied to the screen, so CameraX pauses it when the app is hidden and resumes it after.
 */
class PhoneCamera(private val activity: AppCompatActivity) {

    private val imageCapture = ImageCapture.Builder().build()   // the "take a photo" tool
    private val mainThread = ContextCompat.getMainExecutor(activity)
    private var camera: Camera? = null

    /** Which camera to use: back (default, best for scanning) or front. */
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    val isFront: Boolean get() = lensFacing == CameraSelector.LENS_FACING_FRONT
    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    /** Turns the selected camera on and shows it in [previewView]. Answers true if it started. */
    fun start(previewView: PreviewView, onResult: (started: Boolean) -> Unit = {}) {
        val providerFuture = ProcessCameraProvider.getInstance(activity)
        providerFuture.addListener({
            val started = runCatching {
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                val provider = providerFuture.get()
                provider.unbindAll()
                camera = provider.bindToLifecycle(activity, selector, preview, imageCapture)
            }.isSuccess
            onResult(started)
        }, mainThread)
    }

    /**
     * Switches back ⇄ front and restarts the camera.
     * If the phone has no camera on the other side, it stays on the current one and answers false.
     */
    fun flip(previewView: PreviewView, onResult: (switched: Boolean) -> Unit) {
        val previous = lensFacing
        lensFacing = if (isFront) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT

        start(previewView) { started ->
            if (!started) {
                lensFacing = previous            // no camera on that side → go back to the old one
                start(previewView)
            }
            onResult(started)
        }
    }

    /** Turns the camera off. */
    fun stop() {
        val providerFuture = ProcessCameraProvider.getInstance(activity)
        providerFuture.addListener({ providerFuture.get().unbindAll() }, mainThread)
        camera = null
    }

    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    /** Takes a photo and saves it upright into [file]. Answers true if it worked. */
    fun takePhoto(file: File, onResult: (success: Boolean) -> Unit) {
        imageCapture.takePicture(mainThread, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                image.use { saveUpright(it, file) }
                onResult(true)
            }

            override fun onError(e: ImageCaptureException) {
                Log.e("Verde", "Phone capture failed", e)
                onResult(false)
            }
        })
    }

    /** Phone photos often come out sideways, so we rotate the pixels upright before saving. */
    private fun saveUpright(image: ImageProxy, file: File) {
        val bitmap = image.toBitmap()
        val rotate = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotate, true)
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}
