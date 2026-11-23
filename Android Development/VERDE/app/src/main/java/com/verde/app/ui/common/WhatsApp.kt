package com.verde.app.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.verde.app.R
import com.verde.app.core.WhatsAppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WhatsApp in Verde:
 *  1. share(): share text into WhatsApp (the user picks who gets it)
 *  2. openChat(): open a chat with Verde's WhatsApp number; Verde's server replies
 *  3. sendToMe(): Verde sends the result to the user's own WhatsApp (through our server,
 *     which holds the Meta access token; the app never has it)
 */
object WhatsApp {

    private const val PACKAGE = "com.whatsapp"

    /** What happened when we asked the server to send a message. */
    enum class SendResult { SENT, SAY_HI_FIRST, FAILED }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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
        val link = Uri.parse("https://wa.me/${WhatsAppConfig.VERDE_NUMBER}?text=" + Uri.encode(message))
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, link))
        } catch (e: ActivityNotFoundException) {
            activity.toast(R.string.whatsapp_not_installed)
        }
    }

    /**
     * Asks Verde's server (POST /send) to send [text] to WhatsApp number [to].
     * WhatsApp rule: Verde can only send free text if the user messaged Verde in the
     * last 24 hours, otherwise the answer is SAY_HI_FIRST.
     */
    suspend fun sendToMe(to: String, text: String): SendResult = withContext(Dispatchers.IO) {
        val json = JSONObject().put("to", to).put("text", text).toString()
        val request = Request.Builder()
            .url("${WhatsAppConfig.SERVER_URL}/send")
            .header("X-App-Key", WhatsAppConfig.APP_KEY)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> SendResult.SENT
                    409 -> SendResult.SAY_HI_FIRST
                    else -> SendResult.FAILED
                }
            }
        } catch (e: Exception) {
            SendResult.FAILED                         // no internet, or the server is off
        }
    }
}
