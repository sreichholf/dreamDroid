package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.IdRes
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

/**
 * User-configurable cold-start drawer destination (main drawer entries only).
 * Values are stable string ids stored in SharedPreferences; mapped to menu ids at navigate time.
 */
object StartScreen {
	const val VALUE_SERVICES = "services"
	const val VALUE_EPG = "epg"
	const val VALUE_REMOTE = "remote"
	const val VALUE_CURRENT = "current"
	const val VALUE_ZAP = "zap"
	const val VALUE_TOOLS = "tools"
	const val VALUE_SETTINGS = "settings"

	val VALUES: List<String> = listOf(
		VALUE_SERVICES,
		VALUE_EPG,
		VALUE_REMOTE,
		VALUE_CURRENT,
		VALUE_ZAP,
		VALUE_TOOLS,
		VALUE_SETTINGS,
	)

	fun read(prefs: SharedPreferences): String {
		val raw = prefs.getString(DreamDroid.PREFS_KEY_START_SCREEN, VALUE_SERVICES) ?: VALUE_SERVICES
		return if (raw in VALUES) raw else VALUE_SERVICES
	}

	fun read(context: Context): String =
		read(PreferenceManager.getDefaultSharedPreferences(context))

	@IdRes
	fun menuId(value: String): Int = when (value) {
		VALUE_EPG -> R.id.menu_navigation_epg
		VALUE_REMOTE -> R.id.menu_navigation_remote
		VALUE_CURRENT -> R.id.menu_navigation_current
		VALUE_ZAP -> R.id.menu_navigation_zap
		VALUE_TOOLS -> R.id.menu_navigation_tools
		VALUE_SETTINGS -> R.id.menu_navigation_settings
		else -> R.id.menu_navigation_services
	}

	@IdRes
	fun menuId(prefs: SharedPreferences): Int = menuId(read(prefs))

	@IdRes
	fun menuId(context: Context): Int = menuId(read(context))
}
