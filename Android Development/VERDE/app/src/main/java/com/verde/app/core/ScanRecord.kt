package com.verde.app.core

import com.verde.app.data.PiScanOutcome
import kotlin.math.roundToInt

/**
 * What the AI said about one item, from either camera.
 * The extras (steps, warnings…) come from the Pi; the phone's demo answer leaves them empty.
 */
data class ScanAnswer(
    val wasteClass: WasteClass,
    val confidence: Int,                        // 0–100
    val uncertain: Boolean = false,             // the model isn't sure
    val steps: List<String> = emptyList(),      // disposal steps
    val warnings: List<String> = emptyList(),   // e.g. "rinse first"
    val whyItMatters: String? = null,
    val timeMs: Long? = null                    // benchmark: Pi round trip or phone pipeline
)

/** Turns the Pi's /scan reply into a [ScanAnswer]. */
fun PiScanOutcome.toAnswer() = ScanAnswer(
    wasteClass = WasteClass.fromLabel(result.label ?: ""),
    confidence = ((result.confidence ?: 0.0) * 100).roundToInt(),    // 0.93 → 93
    uncertain = result.uncertain == true,
    steps = result.steps.orEmpty(),
    warnings = result.warnings.orEmpty(),
    whyItMatters = result.whyItMatters,
    timeMs = roundTripMs
)

/** One saved scan. [id] is the time of the scan (milliseconds), so it's unique and sortable. */
data class ScanRecord(
    val id: Long,
    val answer: ScanAnswer,
    val photoPath: String,
    val fromPi: Boolean,         // true = Pi camera, false = phone camera
    val corrected: Boolean = false
) {
    val wasteClass: WasteClass get() = answer.wasteClass
    val confidence: Int get() = answer.confidence

    /** The user said the AI was wrong: same scan, their class instead. */
    fun correctedTo(newClass: WasteClass) =
        copy(answer = answer.copy(wasteClass = newClass), corrected = true)
}
