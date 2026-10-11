package com.verde.app.core

import android.content.Context
import androidx.core.content.edit

/**
 * The Pi's IP address, saved on the phone so it can be changed in the app
 * (long-press the status pill on Home) without rebuilding. Port 5000 is added by PiRepository.
 */
object PiSettings {

    const val DEFAULT_ADDRESS = "10.66.129.96"
    private const val KEY = "pi_address"

    private fun prefs(context: Context) = context.getSharedPreferences("pi", Context.MODE_PRIVATE)

    fun address(context: Context): String =
        prefs(context).getString(KEY, null) ?: DEFAULT_ADDRESS

    fun setAddress(context: Context, address: String) =
        prefs(context).edit { putString(KEY, address.trim()) }
}
