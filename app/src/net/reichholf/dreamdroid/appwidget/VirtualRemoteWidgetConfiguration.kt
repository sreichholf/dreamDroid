package net.reichholf.dreamdroid.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.di.ApplicationScope
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * App widget configure activity. Compose UI; prefs contract unchanged for
 * [VirtualRemoteWidgetProvider] / [WidgetRemoteRequest].
 */
@AndroidEntryPoint
class VirtualRemoteWidgetConfiguration : AppCompatActivity() {
    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        lifecycleScope.launch {
            profileRepository.awaitLoaded()
            val items = profileRepository.profiles().map { profile ->
                ProfileListItem(
                    id = profile.id ?: 0,
                    name = profile.name.orEmpty(),
                    host = profile.host.orEmpty(),
                    active = false
                )
            }
            showProfiles(items)
        }
    }

    private fun showProfiles(items: List<ProfileListItem>) {
        setContent {
            DreamDroidTheme {
                // Match former XML default: QuickZap (simple) checked.
                var isFull by remember { mutableStateOf(false) }
                VirtualRemoteWidgetConfigScreen(
                    profiles = items,
                    isFull = isFull,
                    onStyleFullChange = { isFull = it },
                    onProfileClick = { profile ->
                        lifecycleScope.launch { finishWithProfile(profile.id, isFull) }
                    },
                    onOpenApp = ::openApp
                )
            }
        }
    }

    private fun openApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
        finish()
    }

    private suspend fun finishWithProfile(profileId: Int, isFull: Boolean) {
        saveWidgetConfiguration(profileId, isFull)
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val profile = getWidgetProfile(context, profileRepository, appWidgetId)
        VirtualRemoteWidgetProvider.updateWidget(
            applicationScope,
            context,
            appWidgetManager,
            appWidgetId,
            profile
        )
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
        /** The profile [appWidgetId] was configured with; null if unset or deleted. */
        suspend fun getWidgetProfile(
            context: Context,
            profiles: ProfileRepository,
            appWidgetId: Int
        ): Profile? {
            val profileId = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(getProfileIdKey(appWidgetId), -1)
            if (profileId < 0) return null
            return profiles.profile(profileId)
        }

        fun getProfileIdKey(appWidgetId: Int): String =
            VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + appWidgetId

        fun getIsFullKey(appWidgetId: Int): String =
            VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + appWidgetId + "isFull"

        fun isFull(context: Context, appWidgetId: Int): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + appWidgetId + "isFull",
                false
            )

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
