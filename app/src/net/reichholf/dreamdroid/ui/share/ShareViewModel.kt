package net.reichholf.dreamdroid.ui.share

import android.app.Application
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem

/** What the share or view intent carries. A null [title] falls back to "sent from". */
data class ShareRequest(val url: String?, val title: String?)

/**
 * Profile list and MEDIA_PLAYER_PLAY request for [net.reichholf.dreamdroid.activities.ShareActivity].
 * Activity-scoped, so a rotation keeps the list and an in-flight send instead of
 * reloading profiles and sending again.
 */
class ShareViewModel(application: Application) : AndroidViewModel(application) {
    val listState = ShareProfilesListState()

    var toast by mutableStateOf<String?>(null)
        private set

    var finished by mutableStateOf(false)
        private set

    private var request = ShareRequest(url = null, title = null)
    private val profilesById: MutableMap<Int, Profile> = HashMap()
    private var started = false
    private var sendJob: Job? = null

    fun start(request: ShareRequest) {
        if (started) {
            return
        }
        started = true
        this.request = request
        val app = getApplication<Application>()
        val profiles = AppDatabase.profilesBlocking(app).getProfiles()
        when {
            profiles.size > 1 -> {
                listState.replaceAll(
                    profiles.map { profile ->
                        val id = profile.id ?: 0
                        profilesById[id] = profile
                        ProfileListItem(id, profile.name.orEmpty(), profile.host.orEmpty(), false)
                    }
                )
            }

            profiles.size == 1 -> play(profiles[0])

            else -> toast = app.getString(R.string.no_profile_available)
        }
    }

    fun onProfileClick(item: ProfileListItem) {
        profilesById[item.id]?.let { play(it) }
    }

    fun consumeToast() {
        toast = null
    }

    private fun play(profile: Profile) {
        val url = request.url
        if (url == null) {
            finished = true
            return
        }
        if (listState.progress != null) {
            return
        }
        val app = getApplication<Application>()
        Log.i(LOG_TAG, url)
        Log.i(LOG_TAG, profile.host.orEmpty())
        val title = request.title
            ?: app.getString(
                R.string.sent_from_dreamdroid,
                DateFormat.getDateFormat(app).format(Date())
            )
        val ref = mediaPlayerRef(url, title)
        Log.i(LOG_TAG, ref)
        val params = listOf(NameValuePair("file", ref))
        listState.progress = IndeterminateProgressState(
            title = app.getString(R.string.loading),
            message = app.getString(R.string.loading)
        )
        sendJob?.cancel()
        sendJob = viewModelScope.launchSimpleResultLoad(
            SimpleResultRequestHandler(URIStore.MEDIA_PLAYER_PLAY),
            params,
            profile
        ) { _, _, error ->
            listState.progress = null
            toast = error?.resolve(app) ?: app.getString(R.string.sent_as, title)
            finished = true
        }
    }

    private fun mediaPlayerRef(url: String, title: String): String {
        val encodedUrl = encode(url)
        val encodedTitle = encode(title)
        val uri = Uri.parse(url)
        if ("youtu.be" == uri.host) {
            val vid = uri.path!!.substring(1)
            return "8193:0:1:0:0:0:0:0:0:0:" +
                URLEncoder.encode("yt://$vid", UTF_8) + ":" + encodedTitle
        }
        return "4097:0:1:0:0:0:0:0:0:0:$encodedUrl:$encodedTitle"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")

    private companion object {
        val LOG_TAG: String = ShareViewModel::class.java.simpleName

        // URLEncoder.encode(String, Charset) is API 33; the String overload works on minSdk.
        val UTF_8: String = StandardCharsets.UTF_8.name()
    }
}
