package com.example.verde.ui.common

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import com.example.verde.R
import com.example.verde.databinding.BottomNavBinding
import com.example.verde.ui.ask.AskActivity
import com.example.verde.ui.home.MainActivity

/**
 * The four tabs in the bottom bar and the screen each one opens.
 * Impact and History have no screen yet (null) — tapping them shows "Coming soon".
 */
enum class Tab(val screen: Class<out Activity>?) {
    SCAN(MainActivity::class.java),
    IMPACT(null),
    HISTORY(null),
    ASK(AskActivity::class.java)
}

/** Intent for a tab: reuse it if it's already open, and switch instantly like real tabs. */
fun Activity.tabIntent(screen: Class<out Activity>): Intent = Intent(this, screen)
    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)

/** Opens a tab's screen, or shows "Coming soon" if it isn't built yet. */
fun Activity.openTab(tab: Tab) {
    val screen = tab.screen ?: return toast(R.string.nav_coming_soon)
    startActivity(tabIntent(screen))
}

/** Highlights [current] in the shared bottom bar and opens the other tabs when tapped. */
fun Activity.setUpBottomNav(nav: BottomNavBinding, current: Tab) {
    val tabViews = mapOf(
        Tab.SCAN to nav.navScan,
        Tab.IMPACT to nav.navImpact,
        Tab.HISTORY to nav.navHistory,
        Tab.ASK to nav.navAsk
    )
    tabViews.forEach { (tab, view) ->
        val selected = tab == current
        view.setBackgroundResource(if (selected) R.drawable.bg_nav_selected else 0)
        view.paint(colorOf(if (selected) R.color.verde_green else R.color.verde_tab_inactive))
        view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        view.setOnClickListener { if (!selected) openTab(tab) }
    }
}
