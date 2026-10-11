package com.verde.app.ui.common

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.verde.app.data.piPreviewFrames
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shows the Pi's live video in an ImageView, frame by frame, using the team's
 * piPreviewFrames() (data/PiStream.kt). Only streams while the screen is visible,
 * to save battery, data and the Pi's CPU.
 */
class PiLiveView(
    private val owner: LifecycleOwner,
    private val imageView: ImageView,
    private val onStopped: () -> Unit = {}      // e.g. the Pi was switched off
) {
    private var job: Job? = null
    private var lastFrame: Bitmap? = null       // newest picture from the Pi

    fun show(address: String) {
        hide()
        imageView.isVisible = true
        job = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    piPreviewFrames(address).collect { frame ->
                        lastFrame = frame
                        imageView.setImageBitmap(frame)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onStopped()
                }
            }
        }
    }

    /** Saves the newest frame as a JPEG (the scan photo). Answers false if there's no frame yet. */
    fun saveLastFrame(file: File): Boolean {
        file.delete()                                // never show an old photo
        val frame = lastFrame ?: return false
        file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return true
    }

    fun hide() {
        job?.cancel()
        job = null
        lastFrame = null
        imageView.setImageDrawable(null)
        imageView.isVisible = false
    }
}
