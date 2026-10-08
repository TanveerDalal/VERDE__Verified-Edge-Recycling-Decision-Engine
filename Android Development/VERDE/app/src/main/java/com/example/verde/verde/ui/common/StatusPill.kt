package com.example.verde.ui.common

import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.verde.R
import com.example.verde.pi.PiState

/** How the status pill looks: text, coloured dot, outline and text colour. */
data class PillStyle(
    @StringRes val text: Int,
    @DrawableRes val dot: Int,
    @DrawableRes val background: Int,
    @ColorRes val textColor: Int
)

/** Every look the pill can have, in one place. */
object Pills {
    val PHONE = PillStyle(R.string.status_phone_camera, R.drawable.bg_dot_green, R.drawable.bg_pill, R.color.verde_green_soft)

    fun forPi(state: PiState) = when (state) {
        PiState.CHECKING -> PillStyle(R.string.status_pi_checking, R.drawable.bg_dot_grey, R.drawable.bg_pill, R.color.verde_text_muted)
        PiState.OFFLINE -> PillStyle(R.string.status_pi_offline, R.drawable.bg_dot_red, R.drawable.bg_pill_offline, R.color.verde_offline)
        PiState.NO_CAMERA -> PillStyle(R.string.status_pi_no_camera, R.drawable.bg_dot_amber, R.drawable.bg_pill_warning, R.color.verde_warning)
        PiState.READY -> PillStyle(R.string.status_pi_connected, R.drawable.bg_dot_green, R.drawable.bg_pill, R.color.verde_green_soft)
    }
}

/** Message shown in the green box for each Pi state. */
@StringRes
fun PiState.feedMessage(): Int = when (this) {
    PiState.CHECKING, PiState.READY -> R.string.feed_pi
    PiState.OFFLINE -> R.string.feed_pi_offline
    PiState.NO_CAMERA -> R.string.feed_pi_no_camera
}

/** Applies a [PillStyle] to the pill TextView. */
fun TextView.showPill(style: PillStyle) {
    setText(style.text)
    setBackgroundResource(style.background)
    setTextColor(ContextCompat.getColor(context, style.textColor))
    setCompoundDrawablesRelativeWithIntrinsicBounds(style.dot, 0, 0, 0)
}
