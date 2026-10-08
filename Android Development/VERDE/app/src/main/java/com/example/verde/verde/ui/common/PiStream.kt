package com.example.verde.ui.common

import android.graphics.Color
import android.webkit.WebView
import androidx.core.view.isVisible

/**
 * Shows the Pi's live MJPEG stream inside a WebView.
 * A WebView can play MJPEG directly in an <img>, so no video player is needed.
 */
class PiStream(private val webView: WebView) {

    init {
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    fun show(streamUrl: String) {
        val page = """
            <html><body style="margin:0;height:100vh;background:#08130E">
              <img src="$streamUrl" style="width:100%;height:100%;object-fit:cover">
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
        webView.isVisible = true
    }

    /** Stops downloading the stream and hides it. */
    fun hide() {
        webView.loadUrl("about:blank")
        webView.isVisible = false
    }
}
