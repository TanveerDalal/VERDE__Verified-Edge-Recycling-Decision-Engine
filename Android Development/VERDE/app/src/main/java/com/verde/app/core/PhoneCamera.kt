package com.verde.app.core

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.verde.app.camera.PhoneCaptureResult
import com.verde.app.camera.capturePhoto
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The phone's camera (CameraX) for the Home screen: live picture, back/front switch,
 * and taking a photo through the team's timed pipeline (capturePhoto in PhoneCapture.kt:
 * centre-crop to 224×224 + save the full photo to Pictures/VERDE_captures).
 * Tied to the screen, so CameraX pauses it when the app is hidden and resumes it after.
 */
class PhoneCamera(private val activity: AppCompatActivity) {

    // Best-quality capture: highest resolution, 4:3 (same settings as CameraScreen.kt)
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setJpegQuality(100)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
        )
        .build()

    private val mainThread = ContextCompat.getMainExecutor(activity)
    private val worker = Executors.newSingleThreadExecutor()    // crop/resize/save off the main thread
    private var camera: Camera? = null
    private var lastPreview: PreviewView? = null                 // to restart the camera after an error
    private val handler = Handler(Looper.getMainLooper())

    /** Which camera to use: back (default, best for scanning) or front. */
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    val isFront: Boolean get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    /** Turns the selected camera on and shows it in [previewView]. Answers true if it started. */
    fun start(previewView: PreviewView, onResult: (started: Boolean) -> Unit = {}) {
        lastPreview = previewView
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

    /**
     * Takes a photo with the team pipeline. Answers on the main thread with the result
     * (224×224 image, saved file, timings), or null if it failed.
     */
    fun takePhoto(onResult: (PhoneCaptureResult?) -> Unit) {
        // Camera closed (e.g. after the Result screen, or a camera error)? Restart it and
        // answer null, so the Scan button comes back instead of waiting forever.
        if (camera == null) {
            lastPreview?.let { start(it) }
            mainThread.execute { onResult(null) }
            return
        }

        // Answer exactly once: the photo, a failure, or a time-out if the camera got stuck
        val answered = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun answer(result: PhoneCaptureResult?) {
            if (!answered.compareAndSet(false, true)) return
            handler.removeCallbacks(timeout)
            if (result == null) lastPreview?.let { start(it) }    // restart for the next try
            onResult(result)
        }
        timeout = Runnable { answer(null) }
        handler.postDelayed(timeout, 15_000)

        capturePhoto(
            context = activity,
            imageCapture = imageCapture,
            executor = worker,
            onResult = { result -> mainThread.execute { answer(result) } },
            onFailure = { mainThread.execute { answer(null) } }
        )
    }
}
