package com.verde.app.ui.impact

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.verde.app.R
import com.verde.app.core.ImpactStats
import com.verde.app.core.ScanHistory
import com.verde.app.databinding.ActivityImpactBinding
import com.verde.app.databinding.ItemStatTileBinding
import com.verde.app.ui.common.Tab
import com.verde.app.ui.common.colorOf
import com.verde.app.ui.common.openTab
import com.verde.app.ui.common.setUpBottomNav
import com.verde.app.ui.common.setUpDarkScreen

/**
 * Impact tab: what all the saved scans add up to.
 *  - % of items kept out of landfill (ring)
 *  - items scanned, CO₂ saved (estimate), how often the AI was right, model speed on this phone
 *  - CO₂ in everyday terms (car km, phone charges)
 *  - most common bin + a bar for every bin used
 *  - scans per day for the last 7 days, and the scanning streak
 * Everything is worked out by [ImpactStats] from [ScanHistory], so it is always up to date.
 */
class ImpactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImpactBinding
    private val history by lazy { ScanHistory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImpactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        setUpBottomNav(binding.bottomNav, Tab.IMPACT)
        binding.emptyScanButton.setOnClickListener { openTab(Tab.SCAN) }

        label(binding.statScans, R.string.impact_scans)
        label(binding.statCo2, R.string.impact_co2)
        label(binding.statAi, R.string.impact_ai)
        label(binding.statSpeed, R.string.impact_speed)
    }

    /** Runs every time the tab is shown, so a new scan shows up straight away. */
    override fun onResume() {
        super.onResume()
        render(ImpactStats.from(history.all()))
    }

    private fun render(stats: ImpactStats) {
        binding.emptyCard.isVisible = stats.isEmpty
        binding.statsGroup.isVisible = !stats.isEmpty
        if (stats.isEmpty) return

        // Ring + "18 of 23 items"
        binding.keptOutRing.setProgressCompat(0, false)
        binding.keptOutRing.setProgressCompat(stats.keptOutPercent, true)
        countUp(binding.keptOutPercent, stats.keptOutPercent) { getString(R.string.percent_value, it) }
        binding.keptOutCount.text = getString(R.string.impact_kept_out, stats.keptOutOfLandfill, stats.total)

        // Number tiles
        countUp(binding.statScans.statValue, stats.total) { it.toString() }
        binding.statScans.statHint.setText(R.string.impact_scans_hint)

        binding.statCo2.statValue.text = getString(R.string.impact_co2_value, stats.co2SavedKg)
        binding.statCo2.statValue.setTextColor(colorOf(R.color.verde_green))
        binding.statCo2.statHint.setText(R.string.impact_co2_hint)

        countUp(binding.statAi.statValue, stats.aiAgreedPercent) { getString(R.string.percent_value, it) }
        binding.statAi.statHint.text = getString(R.string.impact_ai_hint, stats.corrected)

        binding.statSpeed.statValue.text = stats.avgInferenceMs
            ?.let { getString(R.string.impact_speed_value, it.toInt()) }
            ?: getString(R.string.impact_speed_none)
        binding.statSpeed.statHint.setText(R.string.impact_speed_hint)

        // Everyday terms
        binding.carText.text = getString(R.string.impact_car, stats.carKm)
        binding.phoneText.text = getString(R.string.impact_phone, stats.phoneCharges)

        renderBins(stats)

        // Week + streak
        binding.weekChart.setDays(stats.lastSevenDays)
        binding.streakText.text = if (stats.streakDays > 0) getString(R.string.impact_streak, stats.streakDays)
        else getString(R.string.impact_streak_none)
        binding.streakText.setTextColor(colorOf(if (stats.streakDays > 0) R.color.verde_warning else R.color.verde_text_faint))
        binding.streakText.setTypeface(null, if (stats.streakDays > 0) Typeface.BOLD else Typeface.NORMAL)

        slideIn(binding.heroCard, binding.statScans.root, binding.statCo2.root, binding.statAi.root,
            binding.statSpeed.root, binding.equalCard, binding.binsCard, binding.weekCard)
    }

    /** "Most common bin" + one coloured bar per bin, longest first. */
    private fun renderBins(stats: ImpactStats) {
        val top = stats.topBin ?: return
        val topColour = colorOf(top.bin.colour)
        val topName = getString(top.bin.shortLabel)
        binding.topBinTile.text = topName.take(1)
        binding.topBinTile.backgroundTintList = ColorStateList.valueOf(topColour)
        binding.topBinName.text = getString(R.string.impact_top_bin, topName)
        binding.topBinName.setTextColor(topColour)
        binding.topBinDetail.text = getString(R.string.impact_top_bin_detail,
            top.count, top.count * 100 / stats.total)

        binding.binBars.removeAllViews()
        val dp = resources.displayMetrics.density
        val most = top.count

        for (entry in stats.bins) {
            val colour = colorOf(entry.bin.colour)
            val isTop = entry == top
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }
            val name = TextView(this).apply {
                setText(entry.bin.shortLabel)
                textSize = 13f
                maxLines = 1
                setTextColor(colorOf(if (isTop) R.color.verde_text_main else R.color.verde_text_muted))
                if (isTop) setTypeface(typeface, Typeface.BOLD)
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = 0
                progressDrawable = ContextCompat.getDrawable(context, R.drawable.progress_verde)?.mutate()
                progressTintList = ColorStateList.valueOf(colour)
            }
            val count = TextView(this).apply {
                text = getString(R.string.impact_bin_count, entry.count)
                textSize = 13f
                gravity = Gravity.END
                setTextColor(colorOf(if (isTop) R.color.verde_text_main else R.color.verde_text_muted))
                if (isTop) setTypeface(typeface, Typeface.BOLD)
            }
            row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.40f))
            row.addView(bar, LinearLayout.LayoutParams(0, (10 * dp).toInt(), 0.48f).apply {
                marginStart = (8 * dp).toInt()
            })
            row.addView(count, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.12f))
            binding.binBars.addView(row)

            // Grow the bar from 0 (a sliver minimum so small bins still show)
            val target = (entry.count * 1000 / most).coerceAtLeast(30)
            ObjectAnimator.ofInt(bar, "progress", 0, target).apply {
                duration = 700
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun label(tile: ItemStatTileBinding, text: Int) = tile.statLabel.setText(text)

    /** Counts a number up from 0 (e.g. 0 → 23) in under a second. */
    private fun countUp(view: TextView, target: Int, format: (Int) -> String) {
        if (target <= 0) {
            view.text = format(0)
            return
        }
        ValueAnimator.ofInt(0, target).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { view.text = format(it.animatedValue as Int) }
            start()
        }
    }

    /** Cards fade in and slide up one after another. */
    private fun slideIn(vararg views: View) {
        val shift = 24 * resources.displayMetrics.density
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = shift
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 60L)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
