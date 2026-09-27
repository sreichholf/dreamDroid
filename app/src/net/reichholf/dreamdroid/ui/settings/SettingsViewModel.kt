package net.reichholf.dreamdroid.ui.settings

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Owns [SettingsState] for the settings destination.
 *
 * Preference values stay in SharedPreferences. [savedStateHandle] is required by the
 * default factory and is not a second copy of those values.
 */
class SettingsViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val state: SettingsState = SettingsState.create(getApplication<Application>())

    var message by mutableStateOf<String?>(null)
        private set

    var messageDuration by mutableStateOf(Toast.LENGTH_LONG)
        private set

    private val pendingMessages = ArrayDeque<PendingMessage>()

    private data class PendingMessage(val text: String, val duration: Int)

    private fun postMessage(text: String, duration: Int) {
        if (message == null) {
            messageDuration = duration
            message = text
        } else {
            pendingMessages.addLast(PendingMessage(text, duration))
        }
    }

    fun consumeMessage() {
        val next = pendingMessages.removeFirstOrNull()
        if (next == null) {
            message = null
        } else {
            messageDuration = next.duration
            message = next.text
        }
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
                postMessage(app.getString(R.string.reset_cache_done), Toast.LENGTH_SHORT)
            }
        }
    }
}
