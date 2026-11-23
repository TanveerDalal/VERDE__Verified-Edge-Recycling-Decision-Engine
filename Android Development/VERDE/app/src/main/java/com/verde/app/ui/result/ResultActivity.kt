package com.verde.app.ui.result

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.verde.app.R
import com.verde.app.core.Guess
import com.verde.app.core.LlamaClient
import com.verde.app.core.ScanHistory
import com.verde.app.core.ScanRecord
import com.verde.app.core.WasteClass
import com.verde.app.core.WhatsAppConfig
import com.verde.app.databinding.ActivityResultBinding
import com.verde.app.ui.ask.AskActivity
import com.verde.app.ui.common.colorOf
import com.verde.app.ui.common.loadPhoto
import com.verde.app.ui.common.WhatsApp
import com.verde.app.ui.common.setUpDarkScreen
import com.verde.app.ui.common.toast
import com.verde.app.ui.guide.BinGuideActivity
import kotlinx.coroutines.launch

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
        binding.similarChip.setOnClickListener { correctTo(alternatives().getOrNull(0)?.wasteClass ?: record.wasteClass.similar) }
        binding.thirdChip.setOnClickListener { alternatives().getOrNull(1)?.let { correctTo(it.wasteClass) } }
        binding.otherChip.setOnClickListener { chooseClass() }
        binding.shareButton.setOnClickListener { shareOnWhatsApp() }
        binding.sendToMeButton.setOnClickListener { sendToMyWhatsApp() }
        binding.sendToMeButton.setOnLongClickListener { askMyNumber(); true }   // change number
        binding.askButton.setOnClickListener {
            val question = getString(R.string.ask_about_item, getString(record.wasteClass.label).lowercase())
            startActivity(AskActivity.newIntent(this, question))
        }
        binding.scanAgainButton.setOnClickListener { finish() }   // back to Home to scan again

        render()
        askLlamaForAdvice()
    }

    /**
     * No steps yet (phone scan, or the Pi sent none)? Ask Llama on the Pi to write them.
     * The built-in advice shows meanwhile, and stays if the Pi can't be reached.
     */
    private fun askLlamaForAdvice() {
        if (record.corrected || record.answer.steps.isNotEmpty()) return
        binding.llamaStatusText.isVisible = true
        lifecycleScope.launch {
            val label = getString(record.wasteClass.label)
            val fact = getString(record.wasteClass.advice)              // Verde's checked advice
            val llama = LlamaClient.advice(this@ResultActivity, label, record.confidence, fact)
            binding.llamaStatusText.isVisible = false
            if (llama == null || record.corrected) return@launch

            record = record.copy(answer = record.answer.copy(
                steps = llama.steps,
                warnings = (record.answer.warnings + llama.warnings).distinct(),
                whyItMatters = llama.whyItMatters
            ))
            history.update(record)                       // remember it for next time
            render()
        }
    }

    /** The model's other guesses (not the shown class), best first. Empty for Pi answers. */
    private fun alternatives(): List<Guess> =
        record.answer.guesses.filter { it.wasteClass != record.wasteClass }.take(2)

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

        binding.adviceText.text = adviceText()
        // "Not right?" chips: the model's 2nd and 3rd guesses (or the usual look-alike class)
        val others = alternatives()
        binding.similarChip.text = others.getOrNull(0)
            ?.let { getString(R.string.its_class_pct, getString(it.wasteClass.label), it.percent) }
            ?: getString(R.string.its_class, getString(item.similar.label))
        binding.thirdChip.isVisible = others.size > 1
        others.getOrNull(1)?.let {
            binding.thirdChip.text = getString(R.string.its_class_pct, getString(it.wasteClass.label), it.percent)
        }

        // Extras from the Pi: "not sure" banner, warnings, and the benchmark time
        val answer = record.answer
        renderGuesses()
        binding.uncertainText.isVisible = answer.uncertain && !record.corrected
        if (answer.uncertain && answer.guesses.isNotEmpty()) {
            val names = answer.guesses.joinToString(" or ") { getString(it.wasteClass.label).lowercase() }
            binding.uncertainText.text = getString(R.string.uncertain_guesses, record.confidence, names)
        }
        binding.warningsText.isVisible = answer.warnings.isNotEmpty()
        binding.warningsText.text = answer.warnings.joinToString("\n") { getString(R.string.warning_line, it) }

        binding.timingText.isVisible = answer.timeMs != null
        answer.timeMs?.let {
            binding.timingText.text = when {
                answer.inferenceMs != null ->                 // the phone's ExecuTorch model
                    getString(R.string.timing_phone_model, answer.inferenceMs, it)
                record.fromPi -> getString(R.string.timing_pi, it)
                else -> getString(R.string.timing_phone, it)
            }
        }
    }

    /**
     * Llama's steps (1. 2. 3.) + "why it matters" when we have them (from the Pi's /scan or /advice).
     * Otherwise (Pi off, or you corrected the class) the app's built-in advice.
     */
    private fun adviceText(): String {
        val answer = record.answer
        if (record.corrected || answer.steps.isEmpty()) return getString(record.wasteClass.advice)

        val steps = answer.steps.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n")
        val why = answer.whyItMatters?.let { "\n\n" + getString(R.string.why_it_matters, it) } ?: ""
        return steps + why
    }

    /** "Cardboard → Recycling bin. Flatten boxes…" sent to WhatsApp; the user picks who gets it. */
    private fun shareOnWhatsApp() = WhatsApp.share(this, resultText())

    private fun resultText(): String {
        val item = record.wasteClass
        return getString(R.string.share_result_format, getString(item.label), getString(item.bin.label), adviceText())
    }

    /**
     * "Send to my WhatsApp": Verde's own WhatsApp number sends this result to the user.
     * The first time, we ask for their number (long-press the button to change it).
     */
    private fun sendToMyWhatsApp() {
        val myNumber = WhatsAppConfig.myNumber(this) ?: return askMyNumber(thenSend = true)

        binding.sendToMeButton.isEnabled = false
        binding.sendToMeButton.setText(R.string.sending)
        lifecycleScope.launch {
            val result = WhatsApp.sendToMe(myNumber, resultText())
            binding.sendToMeButton.isEnabled = true
            binding.sendToMeButton.setText(R.string.send_to_my_whatsapp)
            when (result) {
                WhatsApp.SendResult.SENT -> toast(R.string.whatsapp_sent)
                WhatsApp.SendResult.FAILED -> toast(R.string.whatsapp_send_failed)
                WhatsApp.SendResult.SAY_HI_FIRST -> {
                    toast(R.string.whatsapp_say_hi_first)
                    WhatsApp.openChat(this@ResultActivity, getString(R.string.whatsapp_hello))
                }
            }
        }
    }

    /** Asks for the user's WhatsApp number (with country code) and saves it. */
    private fun askMyNumber(thenSend: Boolean = false) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = getString(R.string.my_whatsapp_hint)
            setText(WhatsAppConfig.myNumber(this@ResultActivity).orEmpty())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.my_whatsapp_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val number = input.text.toString()
                if (number.count { it.isDigit() } < 8) return@setPositiveButton
                WhatsAppConfig.setMyNumber(this, number)
                if (thenSend) sendToMyWhatsApp()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** The user says the AI was wrong: save their choice and show it. */
    /** The chart: one bar per class, best first. The class shown now is bold, in its bin colour. */
    private fun renderGuesses() {
        val guesses = record.answer.guesses.sortedByDescending { it.percent }
        binding.guessesCard.isVisible = guesses.isNotEmpty()
        binding.guessesBars.removeAllViews()
        val dp = resources.displayMetrics.density

        for (guess in guesses) {
            val chosen = guess.wasteClass == record.wasteClass
            val barColour = colorOf(if (chosen) guess.wasteClass.bin.colour else R.color.verde_text_faint)
            val textColour = colorOf(if (chosen) R.color.verde_text_main else R.color.verde_text_muted)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (7 * dp).toInt(), 0, (7 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener { if (guess.wasteClass != record.wasteClass) correctTo(guess.wasteClass) }
            }
            val name = TextView(this).apply {
                text = getString(guess.wasteClass.label)
                textSize = 13f
                setTextColor(textColour)
                if (chosen) setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = guess.percent.coerceAtLeast(1)            // a sliver even at 0%
                progressDrawable = ContextCompat.getDrawable(context, R.drawable.progress_verde)?.mutate()
                progressTintList = ColorStateList.valueOf(barColour)
            }
            val percent = TextView(this).apply {
                text = getString(R.string.percent_value, guess.percent)
                textSize = 13f
                gravity = Gravity.END
                setTextColor(textColour)
                if (chosen) setTypeface(typeface, Typeface.BOLD)
            }
            row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f))
            row.addView(bar, LinearLayout.LayoutParams(0, (8 * dp).toInt(), 0.43f).apply {
                marginStart = (8 * dp).toInt()
            })
            row.addView(percent, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.15f))
            binding.guessesBars.addView(row)
        }
    }

    /** "Other…": pick any of the 8 classes (e.g. General waste for a ceramic cup). */
    private fun chooseClass() {
        val classes = WasteClass.values()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_class)
            .setItems(classes.map { getString(it.label) }.toTypedArray()) { _, which -> correctTo(classes[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun correctTo(wasteClass: WasteClass) {
        history.correct(record.id, wasteClass)
        record = record.correctedTo(wasteClass)
        render()
    }

    private fun showPhoto() {
        val photo = loadPhoto(record.photoPath, shrink = 1) ?: return
        binding.photoImage.setImageBitmap(photo)
        binding.photoImage.isVisible = true
        binding.photoPlaceholder.isVisible = false
        binding.photoLabel.isVisible = false
        binding.photoCard.clipToOutline = true
    }
}
