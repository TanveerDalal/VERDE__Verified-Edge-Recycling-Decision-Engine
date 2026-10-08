package com.example.verde.ui.result

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.example.verde.R
import com.example.verde.data.ScanHistory
import com.example.verde.data.ScanRecord
import com.example.verde.data.WasteClass
import com.example.verde.databinding.ActivityResultBinding
import com.example.verde.ui.ask.AskActivity
import com.example.verde.ui.common.colorOf
import com.example.verde.ui.common.loadPhoto
import com.example.verde.ui.common.WhatsApp
import com.example.verde.ui.common.setUpDarkScreen
import com.example.verde.ui.guide.BinGuideActivity

/** Result: the photo, the class, its bin (colour-coded), advice, and "Not right? It's …". */
class ResultActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SCAN_ID = "scan_id"

        /** Opens the result of a saved scan (from Scan, Home or History). */
        fun newIntent(context: Context, scanId: Long): Intent =
            Intent(context, ResultActivity::class.java).putExtra(EXTRA_SCAN_ID, scanId)
    }

    private lateinit var binding: ActivityResultBinding
    private val history by lazy { ScanHistory(this) }
    private lateinit var record: ScanRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)

        record = history.find(intent.getLongExtra(EXTRA_SCAN_ID, -1)) ?: run {
            finish()          // the scan was deleted
            return
        }

        showPhoto()
        binding.backButton.setOnClickListener { finish() }
        binding.binCard.setOnClickListener { startActivity(Intent(this, BinGuideActivity::class.java)) }
        binding.similarChip.setOnClickListener { correctTo(record.wasteClass.similar) }
        binding.shareButton.setOnClickListener { shareOnWhatsApp() }
        binding.askButton.setOnClickListener {
            val question = getString(R.string.ask_about_item, getString(record.wasteClass.label).lowercase())
            startActivity(AskActivity.newIntent(this, question))
        }
        binding.scanAgainButton.setOnClickListener { finish() }   // back to Home to scan again

        render()
    }

    /** Fills the screen from [record]. */
    private fun render() {
        val item = record.wasteClass
        val binColour = colorOf(item.bin.colour)

        binding.classNameText.setText(item.label)
        if (record.corrected) {
            binding.matchText.setText(R.string.corrected_by_you)
            binding.confidenceBar.isInvisible = true
        } else {
            binding.matchText.text = getString(R.string.match_format, record.confidence)
            binding.confidenceBar.isInvisible = false
            binding.confidenceBar.progress = record.confidence
        }

        binding.binCard.strokeColor = binColour
        binding.binTile.backgroundTintList = ColorStateList.valueOf(binColour)
        binding.binNameText.setText(item.bin.label)
        binding.binNameText.setTextColor(binColour)

        binding.adviceText.setText(item.advice)          // TODO: Llama's answer from the Pi
        binding.similarChip.text = getString(R.string.its_class, getString(item.similar.label))
    }

    /** "Cardboard → Recycling bin. Flatten boxes…" sent to WhatsApp; the user picks who gets it. */
    private fun shareOnWhatsApp() {
        val item = record.wasteClass
        val text = getString(
            R.string.share_result_format,
            getString(item.label),
            getString(item.bin.label),
            getString(item.advice)
        )
        WhatsApp.share(this, text)
    }

    /** The user says the AI was wrong: save their choice and show it. */
    private fun correctTo(wasteClass: WasteClass) {
        history.correct(record.id, wasteClass)
        record = record.copy(wasteClass = wasteClass, corrected = true)
        render()
    }

    private fun showPhoto() {
        val photo = loadPhoto(record.photoPath, shrink = 2) ?: return
        binding.photoImage.setImageBitmap(photo)
        binding.photoImage.isVisible = true
        binding.photoPlaceholder.isVisible = false
        binding.photoLabel.isVisible = false
        binding.photoCard.clipToOutline = true
    }
}
