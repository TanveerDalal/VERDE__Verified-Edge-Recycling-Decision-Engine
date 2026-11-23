package com.verde.app.core

import android.content.Context
import androidx.annotation.StringRes
import com.verde.app.R

/**
 * Verde's checked UK recycling facts, found by keywords in the question.
 * Used two ways: as the answer when Llama isn't available, and as the FACT we give
 * Llama with every question, so a small model explains correct advice instead of guessing.
 */
object WasteKnowledge {

    /** Special items first, so "pizza box" isn't answered as plain cardboard. */
    private val specialAnswers: List<Pair<Set<String>, Int>> = listOf(
        setOf("battery", "batteries") to R.string.answer_batteries,
        setOf("pizza") to R.string.answer_pizza,
        setOf("coffee", "cup", "cups") to R.string.answer_coffee_cup,
        setOf("crisp", "crisps", "wrapper", "wrappers") to R.string.answer_crisps,
        setOf("phone", "charger", "cable", "cables", "electronics", "laptop") to R.string.answer_electronics
    )

    private val classKeywords: Map<WasteClass, Set<String>> = mapOf(
        WasteClass.PAPER_CARD to setOf("cardboard", "box", "boxes", "carton", "paper", "newspaper", "magazine", "envelope", "envelopes"),
        WasteClass.PLASTIC to setOf("plastic", "bottle", "bottles", "tub", "tubs", "tray", "trays"),
        WasteClass.METAL to setOf("metal", "can", "cans", "tin", "tins", "foil", "aerosol"),
        WasteClass.GLASS to setOf("glass", "jar", "jars"),
        WasteClass.FOOD_GARDEN to setOf("food", "peel", "peels", "leftovers", "banana", "apple", "garden", "grass", "leaves", "plant", "plants", "branches"),
        WasteClass.TEXTILE to setOf("clothes", "textile", "textiles", "fabric", "shoes", "towel")
    )

    private val greetings = setOf("hi", "hello", "hey")

    fun answer(context: Context, question: String): String {
        fact(context, question)?.let { return it }
        val words = question.lowercase().split(Regex("[^a-z]+")).toSet()
        @StringRes val fallback = if (greetings.any(words::contains)) R.string.answer_hello else R.string.answer_unknown
        return context.getString(fallback)
    }

    /** The checked fact for this question, or null if Verde has none (then Llama answers alone). */
    fun fact(context: Context, question: String): String? {
        val words = question.lowercase().split(Regex("[^a-z]+")).toSet()

        specialAnswers.firstOrNull { (keys, _) -> keys.any(words::contains) }
            ?.let { return context.getString(it.second) }

        classKeywords.entries.firstOrNull { (_, keys) -> keys.any(words::contains) }
            ?.let { return describe(context, it.key) }

        return null
    }

    /** "Glass → Glass recycling. Rinse bottles and jars…" */
    private fun describe(context: Context, item: WasteClass) = context.getString(
        R.string.answer_class_format,
        context.getString(item.label),
        context.getString(item.bin.label),
        context.getString(item.advice)
    )
}
