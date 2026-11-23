package com.verde.app.ui.history

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.verde.app.R
import com.verde.app.core.Bin
import com.verde.app.core.ImpactStats
import com.verde.app.core.ScanHistory
import com.verde.app.core.ScanRecord
import com.verde.app.databinding.ActivityHistoryBinding
import com.verde.app.ui.common.Tab
import com.verde.app.ui.common.colorOf
import com.verde.app.ui.common.dp
import com.verde.app.ui.common.openTab
import com.verde.app.ui.common.setUpBottomNav
import com.verde.app.ui.common.setUpDarkScreen
import com.verde.app.ui.common.toast
import com.verde.app.ui.result.ResultActivity
import java.util.TimeZone

/**
 * History tab: every saved scan, newest first, grouped by day.
 * Each row shows the photo, the class, the bin (in its colour), the camera, the time and a badge:
 * "✓ Corrected" (the user fixed it), "Not sure" (under 60%) or the model's confidence.
 *  - tap a scan → its Result screen
 *  - long-press → delete it
 *  - chips filter by bin, "Clear" deletes everything
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private val history by lazy { ScanHistory(this) }
    private var filter: Bin? = null                       // null = All

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        setUpBottomNav(binding.bottomNav, Tab.HISTORY)

        adapter = HistoryAdapter(
            scope = lifecycleScope,
            onOpen = { startActivity(ResultActivity.newIntent(this, it.id)) },
            onDelete = ::confirmDelete
        )
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter

        binding.clearButton.setOnClickListener { confirmClear() }
        binding.emptyScanButton.setOnClickListener { openTab(Tab.SCAN) }
    }

    /** Runs every time the tab is shown (after a scan, or after a correction on Result). */
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val all = history.all()
        if (filter != null && all.none { it.wasteClass.bin == filter }) filter = null   // that bin is gone
        val shown = all.filter { filter == null || it.wasteClass.bin == filter }

        binding.subtitleText.text = getString(R.string.history_subtitle, all.size, all.count { it.corrected })
        binding.clearButton.isVisible = all.isNotEmpty()
        binding.filterScroll.isVisible = all.isNotEmpty()
        renderChips(all)

        adapter.submitList(withDayHeadings(shown))
        binding.emptyState.isVisible = shown.isEmpty()
        binding.historyList.isVisible = shown.isNotEmpty()
        binding.emptyText.setText(if (all.isEmpty()) R.string.history_empty_text else R.string.history_empty_filter)
        binding.emptyScanButton.isVisible = all.isEmpty()
    }

    /** "Today" heading, its scans, "Yesterday" heading, its scans… */
    private fun withDayHeadings(records: List<ScanRecord>): List<HistoryItem> {
        val zone = TimeZone.getDefault()
        val items = mutableListOf<HistoryItem>()
        var currentDay = -1L
        for (record in records) {                            // already newest first
            val day = ImpactStats.startOfDay(record.id, zone)
            if (day != currentDay) {
                items += HistoryItem.Day(day)
                currentDay = day
            }
            items += HistoryItem.Scan(record)
        }
        return items
    }

    /** "All" + one chip per bin that has scans, with its count: "Mixed recycling 12". */
    private fun renderChips(all: List<ScanRecord>) {
        binding.filterChips.removeAllViews()
        addChip(getString(R.string.history_filter_all) + "  " + all.size, null, colorOf(R.color.verde_green))
        all.groupingBy { it.wasteClass.bin }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .forEach { (bin, count) -> addChip(getString(bin.shortLabel) + "  " + count, bin, colorOf(bin.colour)) }
    }

    private fun addChip(text: String, bin: Bin?, colour: Int) {
        val selected = bin == filter
        val chip = TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(34)
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_outline_chip)
            setTextColor(if (selected) colorOf(R.color.verde_dark) else colour)
            setOnClickListener {
                if (filter != bin) {
                    filter = bin
                    render()
                    binding.historyList.scrollToPosition(0)
                }
            }
        }
        binding.filterChips.addView(chip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, binding.filterChips.dp(34)
        ).apply { marginStart = binding.filterChips.dp(8) })
    }

    private fun confirmDelete(record: ScanRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_delete_title)
            .setMessage(getString(record.wasteClass.label))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                history.delete(record.id)
                toast(R.string.history_deleted)
                render()
            }
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_text)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                history.clear()
                filter = null
                render()
            }
            .show()
    }
}
