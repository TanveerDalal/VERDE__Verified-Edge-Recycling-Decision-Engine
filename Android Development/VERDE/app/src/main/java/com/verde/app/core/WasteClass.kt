package com.verde.app.core

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.verde.app.R

/** UK bins. Each bin keeps one colour everywhere in the app. */
enum class Bin(
    @StringRes val label: Int,       // "Mixed recycling bin"  (Result screen)
    @StringRes val shortLabel: Int,  // "Mixed recycling"      (guide, history, impact)
    @ColorRes val colour: Int
) {
    RECYCLING(R.string.bin_recycling, R.string.bin_recycling_short, R.color.bin_recycling),
    GLASS(R.string.bin_glass, R.string.bin_glass_short, R.color.bin_glass),
    FOOD(R.string.bin_food, R.string.bin_food_short, R.color.bin_food),
    SPECIAL(R.string.bin_special, R.string.bin_special_short, R.color.bin_special),
    TEXTILE(R.string.bin_textile, R.string.bin_textile_short, R.color.bin_textile),
    GENERAL(R.string.bin_general, R.string.bin_general_short, R.color.bin_general);

    /** Counted as "recycled" on the Impact page. */
    val isRecycling: Boolean get() = this == RECYCLING || this == GLASS
}

/**
 * The eight classes of the VERDE model, in the same order as the bin guide.
 * [similarName] is the class it's most often confused with ("Not right? It's ...").
 */
enum class WasteClass(
    @StringRes val label: Int,
    val bin: Bin,
    @StringRes val advice: Int,
    private val similarName: String
) {
    PAPER_CARD(R.string.class_paper_card, Bin.RECYCLING, R.string.advice_paper_card, "GENERAL"),
    PLASTIC(R.string.class_plastic, Bin.RECYCLING, R.string.advice_plastic, "METAL"),
    METAL(R.string.class_metal, Bin.RECYCLING, R.string.advice_metal, "PLASTIC"),
    GLASS(R.string.class_glass, Bin.GLASS, R.string.advice_glass, "PLASTIC"),
    FOOD_GARDEN(R.string.class_food_garden, Bin.FOOD, R.string.advice_food_garden, "GENERAL"),
    TEXTILE(R.string.class_textile, Bin.TEXTILE, R.string.advice_textile, "GENERAL"),
    SPECIAL(R.string.class_special, Bin.SPECIAL, R.string.advice_special, "METAL"),
    GENERAL(R.string.class_general, Bin.GENERAL, R.string.advice_general, "PLASTIC");

    val similar: WasteClass get() = valueOf(similarName)

    companion object {
        /**
         * Turns the Pi's label into a class. The Pi sends its display name
         * ("Paper or cardboard", "Batteries or electricals"), so we match on keywords.
         * Anything unrecognised becomes general waste.
         */
        fun fromLabel(label: String): WasteClass {
            val text = label.trim().lowercase()
            return when {
                "batter" in text || "electric" in text || "special" in text -> SPECIAL
                "food" in text || "garden" in text -> FOOD_GARDEN
                "paper" in text || "card" in text -> PAPER_CARD
                "plastic" in text -> PLASTIC
                "glass" in text -> GLASS
                "metal" in text -> METAL
                "textile" in text || "cloth" in text || "fabric" in text -> TEXTILE
                else -> GENERAL
            }
        }
    }
}