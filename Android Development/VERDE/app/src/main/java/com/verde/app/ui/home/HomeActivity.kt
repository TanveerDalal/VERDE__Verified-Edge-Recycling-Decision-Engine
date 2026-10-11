package com.verde.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.verde.app.MainActivity
import com.verde.app.R
import com.verde.app.core.Classifier
import com.verde.app.core.PhoneCamera
import com.verde.app.core.PiSettings
import com.verde.app.core.PiState
import com.verde.app.core.ScanAnswer
import com.verde.app.core.ScanHistory
import com.verde.app.core.askPiState
import com.verde.app.core.toAnswer
import com.verde.app.data.PiRepository
import com.verde.app.databinding.ActivityHomeBinding
import com.verde.app.ui.common.PiLiveView
import com.verde.app.ui.common.Pills
import com.verde.app.ui.common.Tab
import com.verde.app.ui.common.colorOf
import com.verde.app.ui.common.feedMessage
import com.verde.app.ui.common.paint
import com.verde.app.ui.common.setUpBottomNav
import com.verde.app.ui.common.setUpDarkScreen
import com.verde.app.ui.common.showPill
import com.verde.app.ui.common.toast
import com.verde.app.ui.result.ResultActivity
import java.io.File
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Home (Scan tab): choose a camera, see its feed, scan an item, see the last scan. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var piLive: PiLiveView
    private val phoneCamera by lazy { PhoneCamera(this) }
    private val history by lazy { ScanHistory(this) }
    private val piPhotoFile by lazy { File(cacheDir, "pi_scan.jpg") }   // photo sent back by the Pi

    private var usingPi = true                  // which camera is selected
    private var piState = PiState.CHECKING      // what we last heard from the Pi

    // Android's "Allow camera?" pop-up
    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startPhoneCamera()
            else {
                toast(R.string.camera_permission_needed)
                selectPiCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        setUpBottomNav(binding.bottomNav, Tab.SCAN)

        binding.feedBox.clipToOutline = true      // keep pictures inside the rounded box
        piLive = PiLiveView(this, binding.piFeed) { if (usingPi) checkPi() }   // stream stopped → re-check

        binding.togglePi.setOnClickListener { selectPiCamera() }
        binding.togglePhone.setOnClickListener { selectPhoneCamera() }
        binding.scanButton.setOnClickListener { scan() }
        binding.flipButton.setOnClickListener { flipCamera() }           // back ⇄ front camera

        // Tap the pill = check the Pi again · long-press = change the Pi's address
        binding.pillText.setOnClickListener { if (usingPi) checkPi() }
        binding.pillText.setOnLongClickListener { editPiAddress(); true }

        // Long-press "Verde" = the team's original test screens (MainActivity: timings, Pi address)
        binding.appTitle.setOnLongClickListener {
            startActivity(Intent(this, MainActivity::class.java)); true
        }

        selectPiCamera()
    }

    override fun onResume() {
        super.onResume()
        binding.greeting.setText(greetingForNow())
        showLastScan()
        // Turn the phone camera back on when returning from another screen
        if (!usingPi && hasCameraPermission()) startPhoneCamera()
    }

    // ───────────── Choosing the camera ─────────────

    private fun selectPiCamera() {
        usingPi = true
        highlight(selected = binding.togglePi, other = binding.togglePhone)
        phoneCamera.stop()
        binding.cameraPreview.isVisible = false
        binding.flipButton.isVisible = false      // flipping is only for the phone camera
        checkPi()
    }

    private fun selectPhoneCamera() {
        usingPi = false
        highlight(selected = binding.togglePhone, other = binding.togglePi)
        piLive.hide()
        binding.pillText.showPill(Pills.PHONE)
        binding.feedLabel.setText(R.string.feed_phone)
        binding.feedPlaceholder.isVisible = true

        if (hasCameraPermission()) startPhoneCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startPhoneCamera() {
        phoneCamera.start(binding.cameraPreview) { started ->
            when {
                usingPi -> phoneCamera.stop()                 // user switched back meanwhile
                !started -> toast(R.string.camera_start_failed)
                else -> {
                    binding.cameraPreview.isVisible = true
                    binding.feedPlaceholder.isVisible = false
                    binding.flipButton.isVisible = true
                }
            }
        }
    }

    /** Flip button: switch between the back and front camera. */
    private fun flipCamera() {
        binding.flipButton.isEnabled = false              // wait until the switch is done
        phoneCamera.flip(binding.cameraPreview) { switched ->
            binding.flipButton.isEnabled = true
            if (!switched) toast(R.string.no_other_camera)
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // ───────────── Pi status ─────────────

    private val piAddress: String get() = PiSettings.address(this)

    private fun checkPi() {
        showPiState(PiState.CHECKING)
        lifecycleScope.launch {
            val state = askPiState(piAddress)
            if (usingPi) showPiState(state)
        }
    }

    private fun showPiState(state: PiState) {
        piState = state
        binding.pillText.showPill(Pills.forPi(state))
        binding.feedLabel.setText(state.feedMessage())

        val ready = state == PiState.READY
        binding.feedPlaceholder.isVisible = !ready
        if (ready) piLive.show(piAddress) else piLive.hide()
    }

    /** Long-press the pill: type the Pi's IP (run `hostname -I` on the Pi). */
    private fun editPiAddress() {
        val input = EditText(this).apply {
            setText(piAddress)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pi_address_title)
            .setMessage(R.string.pi_address_hint)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                PiSettings.setAddress(this, input.text.toString())
                selectPiCamera()                          // reconnect with the new address
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ───────────── Scan + last scan ─────────────

    /** "Scan item": take a photo from the selected camera, identify it, open the Result screen. */
    private fun scan() {
        if (usingPi && piState != PiState.READY) {
            toast(R.string.pi_not_connected)
            return
        }
        binding.scanButton.isEnabled = false          // stop double taps
        if (usingPi) scanWithPi() else scanWithPhone()
    }

    /**
     * The Pi takes the photo AND answers (team's PiRepository.scan → POST /scan).
     * The photo shown on Result is the last frame of the live view.
     */
    private fun scanWithPi() {
        lifecycleScope.launch {
            try {
                val pi = PiRepository(piAddress)
                val answer = pi.scan().toAnswer()
                piLive.saveLastFrame(piPhotoFile)                       // no frame → placeholder
                openResult(answer, piPhotoFile, fromPi = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanFailed(R.string.pi_not_connected)
            }
        }
    }

    /** Phone: team pipeline (224×224 crop + full photo to VERDE_captures), then the classifier. */
    private fun scanWithPhone() {
        phoneCamera.takePhoto { photo ->
            if (photo == null) scanFailed(R.string.photo_failed)
            else openResult(Classifier.classify(photo), photo.file, fromPi = false)
        }
    }

    /** Saves the scan to history and opens the Result screen. */
    private fun openResult(answer: ScanAnswer, photo: File, fromPi: Boolean) {
        val record = history.add(answer, photo, fromPi)
        binding.scanButton.isEnabled = true
        startActivity(ResultActivity.newIntent(this, record.id))
    }

    private fun scanFailed(@StringRes message: Int) {
        binding.scanButton.isEnabled = true
        toast(message)
    }

    /** "Last scan · 2 min ago — Cardboard → Recycling". Hidden until there is a scan. */
    private fun showLastScan() {
        val last = history.latest()
        binding.lastScanCard.isVisible = last != null
        if (last == null) return

        val name = getString(last.wasteClass.label)
        val colour = colorOf(last.wasteClass.bin.colour)
        binding.lastScanTile.text = name.take(1)
        binding.lastScanTile.backgroundTintList = ColorStateList.valueOf(colour)
        binding.lastScanTime.text = getString(R.string.last_scan_time, DateUtils.getRelativeTimeSpanString(last.id))
        binding.lastScanResult.text = getString(R.string.last_scan_result, name, getString(last.wasteClass.bin.shortLabel))
        binding.lastScanCard.setOnClickListener { startActivity(ResultActivity.newIntent(this, last.id)) }
    }

    private fun greetingForNow(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    /** Selected = green background + dark text/icon. Other = grey text/icon. */
    private fun highlight(selected: TextView, other: TextView) {
        selected.setBackgroundResource(R.drawable.bg_toggle_selected)
        other.background = null
        selected.paint(colorOf(R.color.verde_dark))
        other.paint(colorOf(R.color.verde_toggle_inactive))
    }
}
