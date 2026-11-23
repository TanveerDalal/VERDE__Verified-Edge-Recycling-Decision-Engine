package com.verde.app.ui.history

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.verde.app.R
import com.verde.app.core.ScanRecord
import com.verde.app.databinding.ItemHistoryDayBinding
import com.verde.app.databinding.ItemHistoryScanBinding
import com.verde.app.ui.common.colorOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date

/** One line in the History list: a day heading or a scan. */
sealed class HistoryItem {
    data class Day(val dayStart: Long) : HistoryItem()
    data class Scan(val record: ScanRecord) : HistoryItem()
}

/**
 * History list: day headings ("Today", "Yesterday", "Mon 9 Nov") and scan rows
 * (photo · class · bin · source · time · badge). Photos are shrunk to thumbnails off the
 * main thread and kept in a small memory cache, so scrolling stays smooth.
 */
class HistoryAdapter(
    private val scope: LifecycleCoroutineScope,
    private val onOpen: (ScanRecord) -> Unit,
    private val onDelete: (ScanRecord) -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(Diff) {

    private val thumbnails = object : LruCache<String, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount     // count bytes, not photos
    }
    private val missing = mutableSetOf<String>()               // photos that don't exist (Pi scans)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is HistoryItem.Day -> TYPE_DAY
        is HistoryItem.Scan -> TYPE_SCAN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) DayHolder(ItemHistoryDayBinding.inflate(inflater, parent, false))
        else ScanHolder(ItemHistoryScanBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.Day -> (holder as DayHolder).bind(item)
            is HistoryItem.Scan -> (holder as ScanHolder).bind(item.record)
        }
    }

    private class DayHolder(private val b: ItemHistoryDayBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(day: HistoryItem.Day) {
            val context = b.root.context
            b.dayText.text = when {
                DateUtils.isToday(day.dayStart) -> context.getString(R.string.history_today)
                isYesterday(day.dayStart) -> context.getString(R.string.history_yesterday)
                else -> DateUtils.formatDateTime(context, day.dayStart,
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
            }
        }

        private fun isYesterday(time: Long): Boolean {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val day = Calendar.getInstance().apply { timeInMillis = time }
            return yesterday.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
        }
    }

    private inner class ScanHolder(private val b: ItemHistoryScanBinding) : RecyclerView.ViewHolder(b.root) {

        init {
            b.scanPhoto.clipToOutline = true                     // round the photo's corners (bg_thumb)
        }

        fun bind(record: ScanRecord) {
            val context = b.root.context
            val item = record.wasteClass
            val colour = context.colorOf(item.bin.colour)
            val name = context.getString(item.label)

            b.scanClass.text = name
            b.scanBin.setText(item.bin.shortLabel)
            b.scanBin.setTextColor(colour)
            b.scanBinDot.backgroundTintList = ColorStateList.valueOf(colour)
            // "Phone camera · 96 ms" (model time on this phone), or just the camera
            val source = context.getString(if (record.fromPi) R.string.source_pi else R.string.source_phone)
            b.scanSource.text = record.answer.inferenceMs
                ?.let { context.getString(R.string.history_source_line, source, "$it ms") }
                ?: source
            b.scanTime.text = DateFormat.getTimeFormat(context).format(Date(record.id))

            // Badge: corrected (green) > not sure (amber) > confidence
            when {
                record.corrected -> badge(R.string.history_badge_corrected, R.drawable.bg_badge_green, R.color.verde_green_text)
                record.answer.uncertain -> badge(R.string.history_badge_unsure, R.drawable.bg_badge_amber, R.color.verde_warning)
                else -> {
                    b.scanBadge.text = context.getString(R.string.percent_value, record.confidence)
                    b.scanBadge.background = null
                    b.scanBadge.setTextColor(context.colorOf(R.color.verde_text_faint))
                }
            }

            // Letter tile first; the photo covers it once loaded
            b.scanTile.text = name.take(1)
            b.scanTile.backgroundTintList = ColorStateList.valueOf(colour)
            showPhoto(record.photoPath)

            b.root.setOnClickListener { onOpen(record) }
            b.root.setOnLongClickListener { onDelete(record); true }
        }

        private fun badge(text: Int, background: Int, colour: Int) {
            b.scanBadge.setText(text)
            b.scanBadge.setBackgroundResource(background)
            b.scanBadge.setTextColor(b.root.context.colorOf(colour))
        }

        private fun showPhoto(path: String) {
            b.scanPhoto.tag = path
            val cached = thumbnails.get(path)
            if (cached != null) {
                b.scanPhoto.setImageBitmap(cached)
                b.scanPhoto.visibility = View.VISIBLE
                return
            }
            b.scanPhoto.setImageDrawable(null)
            b.scanPhoto.visibility = View.INVISIBLE
            if (path in missing) return

            scope.launch {
                val thumb = withContext(Dispatchers.IO) { loadThumbnail(path) }
                if (thumb == null) {
                    missing += path
                    return@launch
                }
                thumbnails.put(path, thumb)
                if (b.scanPhoto.tag == path) {                       // row not reused for another scan
                    b.scanPhoto.setImageBitmap(thumb)
                    b.scanPhoto.isVisible = true
                    b.scanPhoto.alpha = 0f
                    b.scanPhoto.animate().alpha(1f).setDuration(150).start()
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(old: HistoryItem, new: HistoryItem) = when {
            old is HistoryItem.Day && new is HistoryItem.Day -> old.dayStart == new.dayStart
            old is HistoryItem.Scan && new is HistoryItem.Scan -> old.record.id == new.record.id
            else -> false
        }

        override fun areContentsTheSame(old: HistoryItem, new: HistoryItem) = old == new
    }

    companion object {
        private const val TYPE_DAY = 0
        private const val TYPE_SCAN = 1
        private const val THUMB_SIZE_PX = 200
        private const val THUMB_CACHE_BYTES = 12 * 1024 * 1024      // ~12 MB of thumbnails

        /** Decodes a photo at about 200 px (not full size) to save memory. Null if there is no photo. */
        fun loadThumbnail(path: String): Bitmap? {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIZE_PX) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
