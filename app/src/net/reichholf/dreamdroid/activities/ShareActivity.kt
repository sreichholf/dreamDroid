/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import java.net.URLEncoder
import java.util.Date
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.share.ShareProfilesListState
import net.reichholf.dreamdroid.ui.share.bindShareProfilesScreen

/**
 * Share / view intent → pick a profile (Compose) → play on the box via MEDIA_PLAYER_PLAY.
 */
class ShareActivity : AppCompatActivity() {
    private var simpleResultJob: Job? = null
    private var shc: SimpleHttpClient? = null
    private lateinit var listState: ShareProfilesListState
    private var shareTitle: String? = null

    private var profiles: List<Profile>? = null
    private val profilesById: MutableMap<Int, Profile> = HashMap()
    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        localNetworkPermissionRequest.ensure(this)
        setContentView(R.layout.share_list_content)
        title = getText(R.string.watch_on_dream)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        listState = ShareProfilesListState()
        val compose = findViewById<ComposeView>(R.id.compose_profiles)
        compose.bindShareProfilesScreen(listState) { item ->
            val profile = profilesById[item.id]
            if (profile != null) {
                playOnDream(profile)
            }
        }
        load()
    }

    override fun onDestroy() {
        listState.progress = null
        simpleResultJob?.cancel(null)
        simpleResultJob = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun playOnDream(p: Profile) {
        var url: String? = null
        val i = intent
        val extras = i.extras
        shc = SimpleHttpClient.getInstance(p)
        if (Intent.ACTION_SEND == i.action) {
            url = extras!!.getString(Intent.EXTRA_TEXT)
        } else if (Intent.ACTION_VIEW == i.action) {
            url = i.dataString
        }

        if (url != null) {
            Log.i(LOG_TAG, url)
            Log.i(LOG_TAG, p.host.orEmpty())

            val time = DateFormat.getDateFormat(this).format(Date())
            var title = getString(R.string.sent_from_dreamdroid, time)
            if (extras != null) {
                // semperVidLinks sends "artist" and "song" attributes for the
                // youtube video titles
                val song = extras.getString("song")
                if (song != null) {
                    val artist = extras.getString("artist")
                    if (artist != null) title = "$artist - $song"
                } else {
                    val tmp = extras.getString("title")
                    if (tmp != null) title = tmp
                }
            }
            shareTitle = title

            val uri = Uri.parse(url)
            url = URLEncoder.encode(url).replace("+", "%20")
            title = URLEncoder.encode(title).replace("+", "%20")

            var ref = "4097:0:1:0:0:0:0:0:0:0:$url:$title"

            if ("youtu.be" == uri.host) {
                val vid = uri.path!!.substring(1)
                ref = String.format(
                    "8193:0:1:0:0:0:0:0:0:0:%s:%s",
                    URLEncoder.encode(String.format("yt://%s", vid)),
                    title
                )
            }
            Log.i(LOG_TAG, ref)
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("file", ref))
            execSimpleResultTask(params)
        } else {
            finish()
        }
    }

    fun load() {
        val dao = AppDatabase.profilesBlocking(this)
        profiles = dao.getProfiles()
        profilesById.clear()
        val profiles = this.profiles!!
        if (profiles.size > 1) {
            val items = ArrayList<ProfileListItem>()
            for (m in profiles) {
                val id = m.id ?: 0
                profilesById[id] = m
                items.add(ProfileListItem(id, m.name.orEmpty(), m.host.orEmpty(), false))
            }
            listState.replaceAll(items)
        } else {
            if (profiles.size == 1) {
                playOnDream(profiles[0])
            } else {
                showToast(getString(R.string.no_profile_available))
            }
        }
    }

    fun execSimpleResultTask(params: ArrayList<NameValuePair>) {
        simpleResultJob?.cancel(null)
        listState.progress = IndeterminateProgressState(
            title = getString(R.string.loading),
            message = getString(R.string.loading)
        )
        val handler = SimpleResultRequestHandler(URIStore.MEDIA_PLAYER_PLAY)
        simpleResultJob = launchSimpleResultLoad(handler, params) { _, result, http ->
            simpleResultJob = null
            onSimpleResult(true, result, http)
        }
    }

    fun onSimpleResult(success: Boolean, result: SimpleResult?, http: SimpleHttpClient) {
        listState.progress = null

        if (shareTitle == null) shareTitle = "..."
        var toastText = getString(R.string.sent_as, shareTitle)
        if (http.hasError()) {
            toastText = http.getErrorText(this) ?: toastText
        }

        showToast(toastText)
        finish()
    }

    fun showToast(text: String?) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        var LOG_TAG: String = ShareActivity::class.java.simpleName
    }
}
