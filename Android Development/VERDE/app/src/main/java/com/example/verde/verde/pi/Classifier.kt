package com.example.verde.pi

import com.example.verde.data.WasteClass
import java.io.File
import kotlin.random.Random

/**
 * Decides what's in a photo.
 *
 * TODO (when the Pi's AI model is ready): send [photo] to the Pi (e.g. POST /classify)
 * and return its answer. This is the ONLY place that needs to change — every screen
 * already uses the answer it gives.
 */
object Classifier {

    data class Answer(val wasteClass: WasteClass, val confidence: Int)

    /** Demo answer for now: a random class, so the app can be tested end to end. */
    fun classify(photo: File, onResult: (Answer) -> Unit) {
        onResult(Answer(WasteClass.entries.random(), Random.nextInt(78, 97)))
    }
}
