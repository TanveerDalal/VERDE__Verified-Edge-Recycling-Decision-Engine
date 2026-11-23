package com.verde.app.core

import android.content.Context
import androidx.core.content.edit

/**
 * Settings for Verde's WhatsApp (Meta WhatsApp Cloud API).
 * The secret access token is NOT here: it stays on the WhatsApp server (whatsapp-server/.env).
 */
object WhatsAppConfig {

    /**
     * Verde's WhatsApp number from Meta (API Setup → "From" test number):
     * country code + number, digits only (no +, no spaces). e.g. "15551234567"
     */
    const val VERDE_NUMBER = "15556606775"          // Meta test number +1 (555) 660-6775

    /** Public https address of verde_whatsapp_server.py (the cloudflared / ngrok link). */
    const val SERVER_URL = "https://flavoring-hurled-defiant.ngrok-free.dev"   // permanent (ngrok)
    /** Must match APP_KEY in whatsapp-server/.env */

    const val APP_KEY = "verde-sanjeev-2026"


    // ── The user's own WhatsApp number, saved on the phone after they type it once ──
    private const val KEY = "my_whatsapp"
    private fun prefs(context: Context) = context.getSharedPreferences("whatsapp", Context.MODE_PRIVATE)

    fun myNumber(context: Context): String? = prefs(context).getString(KEY, null)

    fun setMyNumber(context: Context, number: String) =
        prefs(context).edit { putString(KEY, number.trim()) }
}
