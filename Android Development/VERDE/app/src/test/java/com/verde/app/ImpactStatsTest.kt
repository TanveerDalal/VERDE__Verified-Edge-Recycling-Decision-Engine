package com.verde.app

import com.verde.app.core.Bin
import com.verde.app.core.Co2
import com.verde.app.core.ImpactStats
import com.verde.app.core.ScanAnswer
import com.verde.app.core.ScanRecord
import com.verde.app.core.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** The Impact page's numbers. Run: .\gradlew.bat test */
class ImpactStatsTest {

    private val zone = TimeZone.getTimeZone("Europe/London")
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_763_290_800_000L                  // Sun 16 Nov 2025, 11:00 London

    private fun scan(item: WasteClass, daysAgo: Int = 0, corrected: Boolean = false, ms: Long? = 90) =
        ScanRecord(
            id = now - daysAgo * day - 60_000L,            // a minute before "now" on that day
            answer = ScanAnswer(item, 80, inferenceMs = ms),
            photoPath = "",
            fromPi = false,
            corrected = corrected
        )

    @Test
    fun noScans_isEmpty() {
        val stats = ImpactStats.from(emptyList(), now, zone)
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.keptOutPercent)
        assertEquals(0, stats.aiAgreedPercent)
        assertNull(stats.avgInferenceMs)
        assertNull(stats.topBin)
        assertEquals(7, stats.lastSevenDays.size)
        assertEquals(0, stats.streakDays)
    }

    @Test
    fun generalWaste_isNotKeptOutOfLandfill() {
        val stats = ImpactStats.from(
            listOf(scan(WasteClass.PLASTIC), scan(WasteClass.GLASS), scan(WasteClass.GENERAL), scan(WasteClass.GENERAL)),
            now, zone
        )
        assertEquals(4, stats.total)
        assertEquals(2, stats.keptOutOfLandfill)
        assertEquals(50, stats.keptOutPercent)
    }

    @Test
    fun co2_isTheSumOfEachItem() {
        val stats = ImpactStats.from(listOf(scan(WasteClass.METAL), scan(WasteClass.METAL), scan(WasteClass.GENERAL)), now, zone)
        assertEquals(2 * Co2.savedKg(WasteClass.METAL), stats.co2SavedKg, 1e-9)
        assertEquals(0.0, Co2.savedKg(WasteClass.GENERAL), 0.0)
    }

    @Test
    fun mostCommonBin_countsPlasticMetalAndPaperTogether() {
        val stats = ImpactStats.from(
            listOf(scan(WasteClass.PLASTIC), scan(WasteClass.METAL), scan(WasteClass.PAPER_CARD), scan(WasteClass.GLASS)),
            now, zone
        )
        assertEquals(Bin.RECYCLING, stats.topBin?.bin)
        assertEquals(3, stats.topBin?.count)
        assertEquals(listOf(Bin.RECYCLING, Bin.GLASS), stats.bins.map { it.bin })
    }

    @Test
    fun corrections_lowerTheAiAgreedRate() {
        val stats = ImpactStats.from(
            listOf(scan(WasteClass.PLASTIC), scan(WasteClass.GLASS, corrected = true), scan(WasteClass.METAL), scan(WasteClass.PAPER_CARD)),
            now, zone
        )
        assertEquals(1, stats.corrected)
        assertEquals(75, stats.aiAgreedPercent)
    }

    @Test
    fun modelSpeed_ignoresScansWithoutATime() {
        val stats = ImpactStats.from(listOf(scan(WasteClass.PLASTIC, ms = 80), scan(WasteClass.GLASS, ms = 120), scan(WasteClass.METAL, ms = null)), now, zone)
        assertEquals(100L, stats.avgInferenceMs)
    }

    @Test
    fun week_hasSevenDaysEndingToday() {
        val stats = ImpactStats.from(listOf(scan(WasteClass.PLASTIC), scan(WasteClass.GLASS), scan(WasteClass.METAL, daysAgo = 2), scan(WasteClass.METAL, daysAgo = 9)), now, zone)
        assertEquals(listOf(0, 0, 0, 0, 1, 0, 2), stats.lastSevenDays.map { it.count })
        assertEquals(ImpactStats.startOfDay(now, zone), stats.lastSevenDays.last().dayStart)
    }

    @Test
    fun streak_countsDaysInARow() {
        val records = listOf(scan(WasteClass.PLASTIC, daysAgo = 0), scan(WasteClass.PLASTIC, daysAgo = 1),
            scan(WasteClass.PLASTIC, daysAgo = 2), scan(WasteClass.PLASTIC, daysAgo = 4))
        assertEquals(3, ImpactStats.from(records, now, zone).streakDays)
    }

    @Test
    fun streak_stillCountsIfNothingScannedYetToday() {
        val records = listOf(scan(WasteClass.PLASTIC, daysAgo = 1), scan(WasteClass.PLASTIC, daysAgo = 2))
        assertEquals(2, ImpactStats.from(records, now, zone).streakDays)
    }
}
