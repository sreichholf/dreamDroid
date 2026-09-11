package net.reichholf.dreamdroid.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * App widget configure activity. Compose UI; prefs contract unchanged for
 * [VirtualRemoteWidgetProvider] / [WidgetRemoteRequest].
 */
class VirtualRemoteWidgetConfiguration : AppCompatActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val profiles = AppDatabase.profiles(this).getProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.no_profile_available, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val items = profiles.map { profile ->
            ProfileListItem(
                id = profile.id ?: 0,
                name = profile.name.orEmpty(),
                host = profile.host.orEmpty(),
                active = false,
            )
        }

        setContent {
            DreamDroidTheme {
                // Match former XML default: QuickZap (simple) checked.
                var isFull by remember { mutableStateOf(false) }
                VirtualRemoteWidgetConfigScreen(
                    profiles = items,
                    isFull = isFull,
                    onStyleFullChange = { isFull = it },
                    onProfileClick = { profile -> finishWithProfile(profile.id, isFull) },
                )
            }
        }
    }

    private fun finishWithProfile(profileId: Int, isFull: Boolean) {
        saveWidgetConfiguration(profileId, isFull)
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val profile = getWidgetProfile(context, appWidgetId)
        VirtualRemoteWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId, profile)
        val data = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun saveWidgetConfiguration(profileId: Int, isFull: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putInt(getProfileIdKey(appWidgetId), profileId)
            .putBoolean(getIsFullKey(appWidgetId), isFull)
            .apply()
    }

    companion object {
        @JvmStatic
        fun getWidgetProfile(context: Context, appWidgetId: Int): Profile {
            val profileId = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(getProfileIdKey(appWidgetId), -1)
            return AppDatabase.profiles(context).getProfile(profileId)
        }

        @JvmStatic
        fun getProfileIdKey(appWidgetId: Int): String =
            VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + appWidgetId

        @JvmStatic
        fun getIsFullKey(appWidgetId: Int): String =
            VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + appWidgetId + "isFull"

        @JvmStatic
        fun isFull(context: Context, appWidgetId: Int): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + appWidgetId + "isFull",
                false,
            )

        @JvmStatic
        fun deleteWidgetConfiguration(context: Context, appWidgetId: Int) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            var changed = false
            if (prefs.contains(getProfileIdKey(appWidgetId))) {
                editor.remove(getProfileIdKey(appWidgetId))
                changed = true
            }
            if (prefs.contains(getIsFullKey(appWidgetId))) {
                editor.remove(getIsFullKey(appWidgetId))
                changed = true
            }
            if (changed) {
                editor.apply()
            }
        }
    }
}
