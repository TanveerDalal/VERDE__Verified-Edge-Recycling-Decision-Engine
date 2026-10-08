package com.example.verde.ui.guide

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.verde.data.WasteClass
import com.example.verde.databinding.ActivityBinGuideBinding
import com.example.verde.databinding.ItemBinRowBinding
import com.example.verde.ui.common.colorOf
import com.example.verde.ui.common.setUpDarkScreen

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
