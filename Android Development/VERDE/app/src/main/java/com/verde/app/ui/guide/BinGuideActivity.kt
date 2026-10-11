package com.verde.app.ui.guide

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.verde.app.core.WasteClass
import com.verde.app.databinding.ActivityBinGuideBinding
import com.verde.app.databinding.ItemBinRowBinding
import com.verde.app.ui.common.colorOf
import com.verde.app.ui.common.setUpDarkScreen

/** The nine classes and the colour-coded UK bin for each. */
class BinGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBinGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        binding.backButton.setOnClickListener { finish() }

        for (item in WasteClass.entries) {
            val colour = colorOf(item.bin.colour)
            val name = getString(item.label)
            ItemBinRowBinding.inflate(layoutInflater, binding.binRows, true).apply {
                rowTile.text = name.take(1)
                rowTile.backgroundTintList = ColorStateList.valueOf(colour)
                rowName.text = name
                rowBin.setText(item.bin.shortLabel)
                rowBin.setTextColor(colour)
            }
        }
    }
}
