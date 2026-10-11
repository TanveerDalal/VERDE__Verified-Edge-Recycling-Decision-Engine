package com.verde.app.ui.splash

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.verde.app.R
import com.verde.app.databinding.ActivitySplashBinding
import com.verde.app.core.PiSettings
import com.verde.app.core.PiState
import com.verde.app.core.askPiState
import com.verde.app.ui.common.setUpDarkScreen
import com.verde.app.ui.home.HomeActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

        lifecycleScope.launch {
            // Wait at most 2.5 s, so the splash never hangs when the Pi is off
            val address = PiSettings.address(this@SplashActivity)
            val state = withTimeoutOrNull(2_500) { askPiState(address) } ?: PiState.OFFLINE
            binding.statusText.setText(
                when (state) {
                    PiState.READY -> R.string.splash_pi_ready
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
            startActivity(Intent(this, HomeActivity::class.java))
            finish()          // so Back on Home closes the app instead of returning to the splash
        }, 600)
    }
}
