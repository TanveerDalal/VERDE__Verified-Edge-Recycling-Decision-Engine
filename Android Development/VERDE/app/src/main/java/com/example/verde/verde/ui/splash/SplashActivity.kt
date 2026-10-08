package com.example.verde.ui.splash

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.example.verde.R
import com.example.verde.databinding.ActivitySplashBinding
import com.example.verde.pi.PiClient
import com.example.verde.pi.PiConfig
import com.example.verde.pi.PiState
import com.example.verde.ui.common.setUpDarkScreen
import com.example.verde.ui.home.MainActivity

/**
 * First screen: logo + progress bar while we check the Pi.
 * When both are done, opens the Home screen.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var animationDone = false
    private var piChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)

        PiClient(PiConfig.ADDRESS).checkStatus { state ->
            binding.statusText.setText(
                when (state) {
                    PiState.READY -> R.string.splash_pi_ready
                    PiState.NO_CAMERA -> R.string.splash_pi_no_camera
                    else -> R.string.splash_pi_offline
                }
            )
            piChecked = true
            continueIfReady()
        }

        ValueAnimator.ofInt(0, 100).apply {
            duration = 1500
            addUpdateListener { binding.progress.progress = it.animatedValue as Int }
            doOnEnd {
                animationDone = true
                continueIfReady()
            }
            start()
        }
    }

    private fun continueIfReady() {
        if (!animationDone || !piChecked) return
        // A short pause so the Pi status can be read
        binding.root.postDelayed({
            if (isFinishing) return@postDelayed
            startActivity(Intent(this, MainActivity::class.java))
            finish()          // so Back on Home closes the app instead of returning to the splash
        }, 600)
    }
}
