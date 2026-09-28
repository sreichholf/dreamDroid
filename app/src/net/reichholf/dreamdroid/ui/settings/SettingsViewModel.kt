package net.reichholf.dreamdroid.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Owns [SettingsState] for the settings destination.
 *
 * Preference values stay in SharedPreferences.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val state: SettingsState = SettingsState.create(getApplication<Application>())

    var message by mutableStateOf<String?>(null)
        private set

    private val pendingMessages = ArrayDeque<String>()

    private fun postMessage(text: String) {
        if (message == null) {
            message = text
        } else {
            pendingMessages.addLast(text)
        }
    }

    fun consumeMessage() {
        message = pendingMessages.removeFirstOrNull()
    }

    fun resetUseDrivenCache(allProfiles: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val db = AppDatabase.database(app)
            if (allProfiles) {
                UseDrivenCache.clearAll(db)
            } else {
                val profileId = ProfileRepository.get().requireCurrent().id
                if (profileId != null) {
                    UseDrivenCache.clearForProfile(db, profileId)
                }
            }
            SessionConnectionHolder.shared.onUseDrivenCacheCleared()
            withContext(Dispatchers.Main.immediate) {
                postMessage(app.getString(R.string.reset_cache_done))
            }
        }
    }
}
