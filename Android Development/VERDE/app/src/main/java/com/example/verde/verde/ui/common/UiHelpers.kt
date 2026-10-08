package com.example.verde.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat

/**
 * Every Verde screen is dark and drawn behind the system bars.
 * Makes the status-bar icons white and pads [root] so nothing hides under the
 * status bar, the gesture bar or the keyboard.
 */
fun ComponentActivity.setUpDarkScreen(root: View) {
    enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
        insets
    }
}

/** Colours a TextView's text and its icon together (the icon needs its own tint). */
fun TextView.paint(color: Int) {
    setTextColor(color)
    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
}

fun Context.colorOf(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Loads a photo smaller than full size ([shrink] = 2 → half, 8 → an eighth) to save memory. */
fun loadPhoto(path: String, shrink: Int = 1) =
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = shrink })
