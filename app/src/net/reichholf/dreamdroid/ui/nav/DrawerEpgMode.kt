package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid

/**
 * Last EPG view chosen from drawer EPG or the bouquet service list:
 * bouquet list or MultiEPG. List ↔ MultiEPG jumps on those screens write this.
 */
object DrawerEpgMode {
    const val LIST = "list"
    const val MULTI = "multi"

    fun parse(raw: String?): String = if (raw == MULTI) MULTI else LIST

    fun isMulti(raw: String?): Boolean = parse(raw) == MULTI

    fun isMulti(prefs: SharedPreferences): Boolean =
        isMulti(prefs.getString(DreamDroid.PREFS_KEY_DRAWER_EPG_MODE, LIST))

    fun save(prefs: SharedPreferences, mode: String) {
        prefs.edit().putString(DreamDroid.PREFS_KEY_DRAWER_EPG_MODE, parse(mode)).apply()
    }

    fun saveList(context: Context) {
        save(PreferenceManager.getDefaultSharedPreferences(context), LIST)
    }

    fun saveMulti(context: Context) {
        save(PreferenceManager.getDefaultSharedPreferences(context), MULTI)
    }

    fun isNestedOnListEpg(currentRoute: String?, previousRoute: String?): Boolean =
        routeKey(currentRoute) == PhoneNavRoutes.MULTI_EPG &&
            routeKey(previousRoute) == PhoneNavRoutes.EPG
}
