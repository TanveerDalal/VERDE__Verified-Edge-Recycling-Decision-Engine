package com.verde.app.core

import com.verde.app.camera.PhoneCaptureResult
import kotlin.random.Random

/**
 * Decides what's in a phone photo.
 *
 * TODO (when the AI model is ready): run the model on photo.image (already 224×224,
 * the same crop as the Pi) and return its answer. This is the ONLY place that needs to
 * change. Pi scans get their answer from the Pi instead.
 */
object Classifier {

    /** Demo answer for now: a random class, so the app can be tested end to end. */
    fun classify(photo: PhoneCaptureResult): ScanAnswer = ScanAnswer(
        wasteClass = WasteClass.entries.random(),
        confidence = Random.nextInt(78, 97),
        timeMs = photo.totalMs                  // capture + crop/resize + save
    )
}
