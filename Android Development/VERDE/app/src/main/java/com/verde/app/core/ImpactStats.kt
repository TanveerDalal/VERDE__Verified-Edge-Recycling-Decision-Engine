package com.verde.app.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** How many scans went to one bin. */
data class BinCount(val bin: Bin, val count: Int)

/** Scans on one day ([dayStart] = midnight, in milliseconds). */
data class DayCount(val dayStart: Long, val count: Int)

/**
 * Everything the Impact page shows, worked out from the saved scans.
 * Pure Kotlin (no Android), so it is covered by unit tests (ImpactStatsTest).
 *
 * A corrected scan counts as the class the user chose, because that is what really went in the bin.
 */
data class ImpactStats(
    val total: Int,
    val keptOutOfLandfill: Int,          // every bin except general waste
    val keptOutPercent: Int,             // 0–100
    val co2SavedKg: Double,              // estimate, see [Co2]
    val corrected: Int,                  // scans the user had to correct
    val aiAgreedPercent: Int,            // scans the user did NOT correct, 0–100
    val avgInferenceMs: Long?,           // ExecuTorch model time on this phone (null = no phone scans)
    val bins: List<BinCount>,            // most used first, only bins that were used
    val lastSevenDays: List<DayCount>,   // oldest first, today last (always 7 entries)
    val streakDays: Int                  // days in a row with at least one scan, ending today or yesterday
) {
    val topBin: BinCount? get() = bins.firstOrNull()
    val isEmpty: Boolean get() = total == 0

    /** About how far a petrol car drives to make the same CO₂. */
    val carKm: Double get() = co2SavedKg / Co2.CAR_KG_PER_KM

    /** About how many full smartphone charges make the same CO₂. */
    val phoneCharges: Int get() = (co2SavedKg / Co2.PHONE_CHARGE_KG).roundToInt()

    companion object {
        fun from(
            records: List<ScanRecord>,
            now: Long = System.currentTimeMillis(),
            zone: TimeZone = TimeZone.getDefault()
        ): ImpactStats {
            val total = records.size
            val keptOut = records.count { it.wasteClass.bin != Bin.GENERAL }
            val corrected = records.count { it.corrected }
            val speeds = records.mapNotNull { it.answer.inferenceMs }

            val bins = records.groupingBy { it.wasteClass.bin }.eachCount()
                .map { (bin, count) -> BinCount(bin, count) }
                .sortedWith(compareByDescending<BinCount> { it.count }.thenBy { it.bin.ordinal })

            // Last 7 days, today included
            val today = startOfDay(now, zone)
            val days = (6 downTo 0).map { back -> addDays(today, -back, zone) }
            val perDay = records.groupingBy { startOfDay(it.id, zone) }.eachCount()
            val week = days.map { DayCount(it, perDay[it] ?: 0) }

            // Streak: count back from today (or from yesterday if nothing scanned yet today)
            var day = if ((perDay[today] ?: 0) > 0) today else addDays(today, -1, zone)
            var streak = 0
            while ((perDay[day] ?: 0) > 0) {
                streak++
                day = addDays(day, -1, zone)
            }

            return ImpactStats(
                total = total,
                keptOutOfLandfill = keptOut,
                keptOutPercent = percent(keptOut, total),
                co2SavedKg = records.sumOf { Co2.savedKg(it.wasteClass) },
                corrected = corrected,
                aiAgreedPercent = percent(total - corrected, total),
                avgInferenceMs = if (speeds.isEmpty()) null else speeds.average().roundToInt().toLong(),
                bins = bins,
                lastSevenDays = week,
                streakDays = streak
            )
        }

        private fun percent(part: Int, whole: Int) =
            if (whole == 0) 0 else (part * 100.0 / whole).roundToInt()

        fun startOfDay(time: Long, zone: TimeZone): Long = Calendar.getInstance(zone).run {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

        /** Calendar maths, so days stay right across the clock change (23- or 25-hour days). */
        private fun addDays(dayStart: Long, days: Int, zone: TimeZone): Long = Calendar.getInstance(zone).run {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
            timeInMillis
        }
    }
}

/**
 * Rough CO₂ saved per item when it goes in the right bin instead of general waste (kg CO₂e).
 * Average item weight × the saving per kg for recycling it, based on typical UK figures
 * (WRAP / DEFRA conversion factors). They are estimates for motivation, not exact accounting.
 */
object Co2 {
    const val CAR_KG_PER_KM = 0.17          // average UK petrol car
    const val PHONE_CHARGE_KG = 0.008       // one full smartphone charge

    fun savedKg(item: WasteClass): Double = when (item) {
        WasteClass.PAPER_CARD -> 0.05       // ~100 g of card
        WasteClass.PLASTIC -> 0.05          // ~30 g bottle or tub
        WasteClass.METAL -> 0.14            // ~15 g aluminium can (recycling saves ~95% of the energy)
        WasteClass.GLASS -> 0.12            // ~350 g bottle or jar
        WasteClass.FOOD_GARDEN -> 0.08      // ~150 g, no landfill methane
        WasteClass.TEXTILE -> 1.0           // ~300 g of clothing reused
        WasteClass.SPECIAL -> 0.10          // batteries / small electricals recovered safely
        WasteClass.GENERAL -> 0.0           // nothing saved
    }
}
