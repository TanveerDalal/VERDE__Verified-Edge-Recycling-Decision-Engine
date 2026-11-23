package com.verde.app.core

import android.content.Context
import androidx.core.content.edit

/**
 * The Pi's IP address, saved on the phone so it can be changed in the app
 * (long-press the status pill on Home) without rebuilding. Port 5000 is added by PiRepository.
 */
object PiSettings {

    const val DEFAULT_ADDRESS = "10.85.100.96"
    private const val KEY = "pi_address"

    private fun prefs(context: Context) = context.getSharedPreferences("pi", Context.MODE_PRIVATE)

    fun address(context: Context): String =
        prefs(context).getString(KEY, null) ?: DEFAULT_ADDRESS

    fun setAddress(context: Context, address: String) =
        prefs(context).edit { putString(KEY, address.trim()) }


    /** Secret for the Pi's Llama server (port 5001), same as VERDE_LLAMA_TOKEN on the Pi. */
    fun llamaToken(context: Context): String = prefs(context).getString("llama_token", "") ?: ""

    fun setLlamaToken(context: Context, token: String) =
        prefs(context).edit { putString("llama_token", token.trim()) }

    /** Ask tab: true = Llama on this phone (1B), false = Llama on the Raspberry Pi (8B). */
    fun llamaOnPhone(context: Context): Boolean = prefs(context).getBoolean("llama_on_phone", true)

    fun setLlamaOnPhone(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean("llama_on_phone", value) }

    /** true = phone-only (model runs on the phone). false = send the photo to the Pi. */
    fun onDevice(context: Context): Boolean = prefs(context).getBoolean("phone_on_device", true)

    fun setOnDevice(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean("phone_on_device", value) }
}