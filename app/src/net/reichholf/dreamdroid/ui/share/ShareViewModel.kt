package net.reichholf.dreamdroid.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What the share or view intent carries. [title] is the media title sent to the box. */
data class ShareRequest(val url: String?, val title: String)

/**
 * [profiles] to pick from when there is more than one. [sending] while the box is asked to
 * play. [userMessage] shows as a toast; [finished] closes the activity.
 */
data class ShareUiState(
    val profiles: List<ProfileListItem> = emptyList(),
    val sending: Boolean = false,
    val userMessage: UiText? = null,
    val finished: Boolean = false
)

/**
 * Profile list and MEDIA_PLAYER_PLAY request for [net.reichholf.dreamdroid.activities.ShareActivity].
 * Activity-scoped, so a rotation keeps the list and an in-flight send instead of
 * reloading profiles and sending again.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val receiver: ReceiverRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private var request = ShareRequest(url = null, title = "")
    private var profilesById: Map<Int, Profile> = emptyMap()
    private var started = false

    fun start(request: ShareRequest) {
        if (started) {
            return
        }
        started = true
        this.request = request
        viewModelScope.launch {
            // A share can start the process; the 1.x profile import runs in that load.
            profiles.awaitLoaded()
            val saved = profiles.profiles()
            when {
                saved.size > 1 -> {
                    profilesById = saved.associateBy { it.id ?: 0 }
                    val items = saved.map { profile ->
                        ProfileListItem(
                            profile.id ?: 0,
                            profile.name.orEmpty(),
                            profile.host.orEmpty(),
                            false
                        )
                    }
                    _uiState.update { it.copy(profiles = items) }
                }

                saved.size == 1 -> play(saved[0])

                else -> _uiState.update {
                    it.copy(userMessage = UiText.Resource(R.string.no_profile_available))
                }
            }
        }
    }

    fun onProfileClick(item: ProfileListItem) {
        profilesById[item.id]?.let { play(it) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun play(profile: Profile) {
        val url = request.url
        if (url == null) {
            _uiState.update { it.copy(finished = true) }
            return
        }
        if (_uiState.value.sending) {
            return
        }
        val title = request.title
        _uiState.update { it.copy(sending = true) }
        viewModelScope.launch {
            val response = receiver.playMedia(profile, mediaPlayerRef(url, title))
            // A failure without text still failed: userMessageText falls back to the generic error.
            val message = if (response.error == null) {
                UiText.Resource(R.string.sent_as, listOf(title))
            } else {
                response.userMessageText()
            }
            _uiState.update {
                it.copy(sending = false, userMessage = message, finished = true)
            }
        }
    }

    private companion object {
        // URLEncoder.encode(String, Charset) is API 33; the String overload works on minSdk.
        val UTF_8: String = StandardCharsets.UTF_8.name()

        fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")

        /** A `4097` service ref for [url]; a youtu.be link becomes a `yt://` ref. */
        fun mediaPlayerRef(url: String, title: String): String {
            val encodedTitle = encode(title)
            // HttpUrl, unlike java.net.URI, accepts shared links with unescaped characters.
            val uri = url.toHttpUrlOrNull()
            if (uri?.host == "youtu.be") {
                val vid = uri.pathSegments.joinToString("/")
                return "8193:0:1:0:0:0:0:0:0:0:" +
                    URLEncoder.encode("yt://$vid", UTF_8) + ":" + encodedTitle
            }
            return "4097:0:1:0:0:0:0:0:0:0:${encode(url)}:$encodedTitle"
        }
    }
}
