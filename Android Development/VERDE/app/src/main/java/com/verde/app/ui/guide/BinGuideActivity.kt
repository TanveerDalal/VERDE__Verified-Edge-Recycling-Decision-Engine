package com.verde.app.ui.guide

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.verde.app.R
import com.verde.app.core.ClassGuide
import com.verde.app.core.WasteClass
import com.verde.app.databinding.ActivityBinGuideBinding
import com.verde.app.databinding.ItemBinRowBinding
import com.verde.app.ui.common.colorOf
import com.verde.app.ui.common.setUpDarkScreen
import java.text.NumberFormat

/**
 * The class guide: the eight classes, each with its UK bin, example items,
 * how many training images it has and how often the model gets it right.
 */
class BinGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBinGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val numbers = NumberFormat.getIntegerInstance()
        binding.trainedOn.text = getString(R.string.guide_trained_on, numbers.format(ClassGuide.totalImages))

        for (item in WasteClass.entries) {
            val stats = ClassGuide.stats.getValue(item)
            val colour = colorOf(item.bin.colour)
            val name = getString(item.label)
            ItemBinRowBinding.inflate(layoutInflater, binding.binRows, true).apply {
                rowTile.text = name.take(1)
                rowTile.backgroundTintList = ColorStateList.valueOf(colour)
                rowName.text = name
                rowBin.setText(item.bin.shortLabel)
                rowBin.setTextColor(colour)
                rowExamples.setText(stats.examples)

                rowBar.progress = (stats.images * 100f / ClassGuide.largestClass).toInt()
                rowBar.progressTintList = ColorStateList.valueOf(colour)
                rowCount.text = getString(R.string.guide_images_format, numbers.format(stats.images))
                rowAccuracy.text = stats.accuracy
                    ?.let { getString(R.string.guide_accuracy_format, it) }
                    ?: getString(R.string.guide_accuracy_tbc)
            }
        }
    }
}
