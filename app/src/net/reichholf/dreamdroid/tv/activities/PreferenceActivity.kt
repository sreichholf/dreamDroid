package net.reichholf.dreamdroid.tv.activities

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.profiles.ProfileEditScreen
import net.reichholf.dreamdroid.ui.profiles.ProfileEditState
import net.reichholf.dreamdroid.ui.settings.SettingsState
import net.reichholf.dreamdroid.ui.settings.TvSettingsScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme

/**
 * TV settings / profile host (Phase 3.1d). Compose Material 3; PreferenceManager keys
 * match the old Leanback XML screens.
 */
class PreferenceActivity : AppCompatActivity() {

    private var profileState: ProfileEditState? = null
    private var editingProfile: Profile? = null
    private var isProfileMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(KEY_PREFS_TYPE)
        isProfileMode = PREFS_TYPE_PROFILE == type
        title = getString(if (isProfileMode) R.string.profile else R.string.settings)

        if (isProfileMode) {
            // RESULT_OK: TvComposeHubHost reloads browse data via Activity Result API.
            setResult(Activity.RESULT_OK)
            val profile = DreamDroid.getCurrentProfile()
            editingProfile = profile
            val state = ProfileEditState.fromProfile(profile)
            profileState = state
            setContent {
                DreamDroidTvTheme {
                    ProfileEditScreen(
                        state = state,
                        saveLabel = getString(R.string.save),
                        onSave = {
                            persistProfile()
                            finish()
                        }
                    )
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

    override fun onPause() {
        if (isProfileMode) {
            persistProfile()
        }
        super.onPause()
    }

    private fun persistProfile() {
        val state = profileState ?: return
        val profile = editingProfile ?: return
        state.applyTo(profile)
        val dao = AppDatabase.profilesBlocking(this)
        dao.updateProfile(profile)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putInt(DreamDroid.CURRENT_PROFILE, profile.id ?: -1).apply()
        DreamDroid.setCurrentProfile(profile)
    }

    companion object {
        const val PREFS_TYPE_GENERIC = "generic"
        const val PREFS_TYPE_PROFILE = "profile"
        const val KEY_PREFS_TYPE = "type"
    }
}
