package com.verde.app.core

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.verde.app.R

/** UK bins. Each bin keeps one colour everywhere in the app. */
enum class Bin(
    @StringRes val label: Int,       // "Recycling bin"  (Result screen)
    @StringRes val shortLabel: Int,  // "Recycling"      (guide, history, impact)
    @ColorRes val colour: Int
) {
    RECYCLING(R.string.bin_recycling, R.string.bin_recycling_short, R.color.bin_recycling),
    GLASS(R.string.bin_glass, R.string.bin_glass_short, R.color.bin_glass),
    FOOD(R.string.bin_food, R.string.bin_food_short, R.color.bin_food),
    GARDEN(R.string.bin_garden, R.string.bin_garden_short, R.color.bin_garden),
    TEXTILE(R.string.bin_textile, R.string.bin_textile_short, R.color.bin_textile),
    GENERAL(R.string.bin_general, R.string.bin_general_short, R.color.bin_general);

    /** Counted as "recycled" on the Impact page. */
    val isRecycling: Boolean get() = this == RECYCLING || this == GLASS
}

/**
 * The nine dataset classes, in the same order as the bin guide.
 * [similarName] is the class it's most often confused with ("Not right? It's …").
 */
enum class WasteClass(
    @StringRes val label: Int,
    val bin: Bin,
    @StringRes val advice: Int,
    private val similarName: String
) {
    CARDBOARD(R.string.class_cardboard, Bin.RECYCLING, R.string.advice_cardboard, "PAPER"),
    PAPER(R.string.class_paper, Bin.RECYCLING, R.string.advice_paper, "CARDBOARD"),
    PLASTIC(R.string.class_plastic, Bin.RECYCLING, R.string.advice_plastic, "MISC_TRASH"),
    METAL(R.string.class_metal, Bin.RECYCLING, R.string.advice_metal, "PLASTIC"),
    GLASS(R.string.class_glass, Bin.GLASS, R.string.advice_glass, "PLASTIC"),
    FOOD_ORGANICS(R.string.class_food_organics, Bin.FOOD, R.string.advice_food_organics, "VEGETATION"),
    VEGETATION(R.string.class_vegetation, Bin.GARDEN, R.string.advice_vegetation, "FOOD_ORGANICS"),
    TEXTILE_TRASH(R.string.class_textile_trash, Bin.TEXTILE, R.string.advice_textile_trash, "MISC_TRASH"),
    MISC_TRASH(R.string.class_misc_trash, Bin.GENERAL, R.string.advice_misc_trash, "PLASTIC");

    val similar: WasteClass get() = valueOf(similarName)

    companion object {
        /**
         * Turns the Pi's label into a class: "Plastic" → PLASTIC, "Food Organics" → FOOD_ORGANICS.
         * Anything else (e.g. "Miscellaneous Trash") → MISC_TRASH, i.e. general waste.
         */
        fun fromLabel(label: String): WasteClass {
            val key = label.trim().uppercase().replace(Regex("[^A-Z]+"), "_")
            return entries.firstOrNull { it.name == key } ?: MISC_TRASH
        }
    }
}
