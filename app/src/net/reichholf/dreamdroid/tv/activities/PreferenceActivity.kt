package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.tv.ui.TvProfilesHost
import net.reichholf.dreamdroid.ui.settings.SettingsState
import net.reichholf.dreamdroid.ui.settings.TvSettingsScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme

/**
 * TV settings / profile host (Phase 3.1d). Compose Material 3; PreferenceManager keys
 * match the old Leanback XML screens.
 */
class PreferenceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(KEY_PREFS_TYPE)
        val isProfileMode = PREFS_TYPE_PROFILE == type
        title = getString(if (isProfileMode) R.string.profile else R.string.settings)

        if (isProfileMode) {
            setContent {
                DreamDroidTvTheme {
                    TvProfilesHost()
                }
            }
        } else {
            val settingsState = SettingsState.create(this)
            setContent {
                DreamDroidTvTheme {
                    val remembered = remember { settingsState }
                    TvSettingsScreen(state = remembered)
                }
            }
        }
    }

    companion object {
        const val PREFS_TYPE_GENERIC = "generic"
        const val PREFS_TYPE_PROFILE = "profile"
        const val KEY_PREFS_TYPE = "type"
    }
}
