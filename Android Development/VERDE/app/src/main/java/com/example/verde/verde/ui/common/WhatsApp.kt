package com.example.verde.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.example.verde.R

/**
 * WhatsApp integration — the two things Android apps are allowed to do:
 *  1. share text into WhatsApp (the user picks who to send it to)
 *  2. open a WhatsApp chat with a phone number
 * No accounts or servers are needed; WhatsApp just has to be installed.
 */
object WhatsApp {

    /**
     * Verde's WhatsApp number: country code + number, digits only (no +, no spaces).
     * TODO: put your own number here — e.g. the Twilio Sandbox number if you set up the bot.
     */
    private const val VERDE_NUMBER = "14155238886"

    private const val PACKAGE = "com.whatsapp"

    /** Opens WhatsApp so the user can send [text] to a friend or group. */
    fun share(activity: Activity, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(PACKAGE)                       // go straight to WhatsApp
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No WhatsApp on this phone → let the user share with any other app instead
            intent.setPackage(null)
            activity.startActivity(Intent.createChooser(intent, null))
        }
    }

    /** Opens a WhatsApp chat with Verde's number, with [message] already typed in. */
    fun openChat(activity: Activity, message: String) {
        val link = Uri.parse("https://wa.me/$VERDE_NUMBER?text=" + Uri.encode(message))
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, link))
        } catch (e: ActivityNotFoundException) {
            activity.toast(R.string.whatsapp_not_installed)
        }
    }
}
