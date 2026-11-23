package com.verde.app.core

import androidx.annotation.StringRes
import com.verde.app.R

/**
 * What the class guide shows for each class, from the 3.0 data analysis.
 * [images] = training images in the final 3.0 dataset (61,824 in total).
 * [accuracy] = share of that class's test images the model got right (recall, %). It stays null
 * until the per-class numbers for EfficientNet-B0 T3 seed 42 on the 3.0 test set are filled in:
 * the guide shows "tbc" for null and "93% right" once a number is set here.
 */
data class ClassStats(
    @StringRes val examples: Int,
    val images: Int,
    val accuracy: Int? = null
)

object ClassGuide {
    val stats: Map<WasteClass, ClassStats> = mapOf(
        WasteClass.PAPER_CARD to ClassStats(R.string.examples_paper_card, images = 9_182),
        WasteClass.PLASTIC to ClassStats(R.string.examples_plastic, images = 8_589),
        WasteClass.METAL to ClassStats(R.string.examples_metal, images = 7_220),
        WasteClass.GLASS to ClassStats(R.string.examples_glass, images = 5_087),
        WasteClass.FOOD_GARDEN to ClassStats(R.string.examples_food_garden, images = 6_767),
        WasteClass.TEXTILE to ClassStats(R.string.examples_textile, images = 7_620),
        WasteClass.SPECIAL to ClassStats(R.string.examples_special, images = 12_718),
        WasteClass.GENERAL to ClassStats(R.string.examples_general, images = 4_641)
    )

    val totalImages: Int get() = stats.values.sumOf { it.images }
    val largestClass: Int get() = stats.values.maxOf { it.images }
}