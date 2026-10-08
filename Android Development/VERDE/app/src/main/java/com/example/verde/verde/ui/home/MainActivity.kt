package com.example.verde.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.verde.R
import com.example.verde.camera.GallerySaver
import com.example.verde.camera.PhoneCamera
import com.example.verde.data.ScanHistory
import com.example.verde.databinding.ActivityMainBinding
import com.example.verde.pi.Classifier
import com.example.verde.pi.PiClient
import com.example.verde.pi.PiConfig
import com.example.verde.pi.PiState
import com.example.verde.ui.common.PiStream
import com.example.verde.ui.common.Pills
import com.example.verde.ui.common.Tab
import com.example.verde.ui.common.colorOf
import com.example.verde.ui.common.feedMessage
import com.example.verde.ui.common.paint
import com.example.verde.ui.common.setUpBottomNav
import com.example.verde.ui.common.setUpDarkScreen
import com.example.verde.ui.common.showPill
import com.example.verde.ui.common.toast
import com.example.verde.ui.result.ResultActivity
import java.io.File
import java.util.Calendar

/** Home (Scan tab): choose a camera, see its feed, scan an item, see the last scan. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var piStream: PiStream
    private val pi = PiClient(PiConfig.ADDRESS)
    private val phoneCamera by lazy { PhoneCamera(this) }
    private val history by lazy { ScanHistory(this) }
    private val photoFile by lazy { File(cacheDir, "scan.jpg") }     // where each new photo is saved

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        setUpBottomNav(binding.bottomNav, Tab.SCAN)

        binding.feedBox.clipToOutline = true      // keep pictures inside the rounded box
        piStream = PiStream(binding.piFeed)

        binding.togglePi.setOnClickListener { selectPiCamera() }
        binding.togglePhone.setOnClickListener { selectPhoneCamera() }
        binding.scanButton.setOnClickListener { scan() }
        binding.pillText.setOnClickListener { if (usingPi) checkPi() }   // tap pill = check again
        binding.flipButton.setOnClickListener { flipCamera() }           // back ⇄ front camera

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
        piStream.hide()
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

    private fun checkPi() {
        showPiState(PiState.CHECKING)
        pi.checkStatus { state -> if (usingPi) showPiState(state) }
    }

    private fun showPiState(state: PiState) {
        piState = state
        binding.pillText.showPill(Pills.forPi(state))
        binding.feedLabel.setText(state.feedMessage())

        val ready = state == PiState.READY
        binding.feedPlaceholder.isVisible = !ready
        if (ready) piStream.show(pi.streamUrl) else piStream.hide()
    }

    // ───────────── Scan + last scan ─────────────

    /** "Scan item": take a photo from the selected camera, identify it, open the Result screen. */
    private fun scan() {
        if (usingPi && piState != PiState.READY) {
            toast(if (piState == PiState.NO_CAMERA) R.string.pi_no_camera else R.string.pi_not_connected)
            return
        }
        binding.scanButton.isEnabled = false          // stop double taps

        if (usingPi) {
            // The Pi takes the photo AND answers (POST /scan)
            pi.scan(photoFile) { answer ->
                if (answer != null) openResult(answer) else scanFailed(R.string.pi_not_connected)
            }
        } else {
            // Phone camera: take the photo here, then identify it (demo answer for now)
            phoneCamera.takePhoto(photoFile) { ok ->
                if (ok) Classifier.classify(photoFile) { openResult(it) } else scanFailed(R.string.photo_failed)
            }
        }
    }

    /** Saves the scan to history and opens the Result screen. */
    private fun openResult(answer: Classifier.Answer) {
        val record = history.add(answer.wasteClass, answer.confidence, photoFile, fromPi = usingPi)
        if (photoFile.exists()) GallerySaver.save(this, photoFile, "verde_${record.id}")   // copy to Pictures/Verde
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
