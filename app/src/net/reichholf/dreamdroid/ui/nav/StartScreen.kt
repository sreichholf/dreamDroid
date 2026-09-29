package net.reichholf.dreamdroid.ui.nav

import android.content.SharedPreferences
import androidx.annotation.IdRes
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

/**
 * User-configurable cold-start drawer destination (main drawer entries only).
 * Values are stable string ids stored in SharedPreferences; mapped to menu ids
 * at navigate time.
 */
object StartScreen {
    const val VALUE_SERVICES = "services"
    const val VALUE_EPG = "epg"
    const val VALUE_REMOTE = "remote"

    /** Legacy start-screen value; mapped to [VALUE_SERVICES] (now-playing on hub). */
    const val VALUE_CURRENT = "current"
    const val VALUE_ZAP = "zap"
    const val VALUE_TOOLS = "tools"

    val VALUES: List<String> = listOf(
        VALUE_SERVICES,
        VALUE_EPG,
        VALUE_REMOTE,
        VALUE_ZAP,
        VALUE_TOOLS
    )

    fun read(prefs: SharedPreferences): String {
        val raw = prefs.getString(
            DreamDroid.PREFS_KEY_START_SCREEN,
            VALUE_SERVICES
        ) ?: VALUE_SERVICES
        return if (raw in VALUES) raw else VALUE_SERVICES
    }

    @IdRes
    fun menuId(value: String): Int = when (value) {
        VALUE_EPG -> R.id.menu_navigation_epg
        VALUE_REMOTE -> R.id.menu_navigation_remote
        VALUE_ZAP -> R.id.menu_navigation_zap
        VALUE_TOOLS -> R.id.menu_navigation_tools
        else -> R.id.menu_navigation_services
    }

    /** PhoneNavHost route for the configured start screen (drawer roots only). */
    fun navRoute(value: String): String = when (value) {
        VALUE_EPG -> PhoneNavRoutes.EPG
        VALUE_REMOTE -> PhoneNavRoutes.REMOTE
        VALUE_ZAP -> PhoneNavRoutes.ZAP
        VALUE_TOOLS -> PhoneNavRoutes.TOOLS
        else -> PhoneNavRoutes.HUB
    }
}
