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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.share.ShareProfilesListState
import net.reichholf.dreamdroid.ui.share.bindShareProfilesScreen
import java.net.URLEncoder
import java.util.Date

/**
 * Share / view intent → pick a profile (Compose) → play on the box via MEDIA_PLAYER_PLAY.
 */
class ShareActivity : AppCompatActivity() {
    private var mSimpleResultJob: Job? = null
    private var mShc: SimpleHttpClient? = null
    private lateinit var mListState: ShareProfilesListState
    private var mTitle: String? = null

    private var mProfiles: List<Profile>? = null
    private val mProfilesById: MutableMap<Int, Profile> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.share_list_content)
        title = getText(R.string.watch_on_dream)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        mListState = ShareProfilesListState()
        val compose = findViewById<ComposeView>(R.id.compose_profiles)
        compose.bindShareProfilesScreen(mListState) { item ->
            val profile = mProfilesById[item.id]
            if (profile != null) {
                playOnDream(profile)
            }
        }
        load()
    }

    override fun onDestroy() {
        mListState.progress = null
        mSimpleResultJob?.cancel(null)
        mSimpleResultJob = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun playOnDream(p: Profile) {
        var url: String? = null
        val i = intent
        val extras = i.extras
        mShc = SimpleHttpClient.getInstance(p)
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
            mTitle = title

            val uri = Uri.parse(url)
            url = URLEncoder.encode(url).replace("+", "%20")
            title = URLEncoder.encode(title).replace("+", "%20")

            var ref = "4097:0:1:0:0:0:0:0:0:0:$url:$title"

            if ("youtu.be" == uri.host) {
                val vid = uri.path!!.substring(1)
                ref = String.format(
                    "8193:0:1:0:0:0:0:0:0:0:%s:%s",
                    URLEncoder.encode(String.format("yt://%s", vid)),
                    title,
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
        val dao = AppDatabase.profiles(this)
        mProfiles = dao.getProfiles()
        mProfilesById.clear()
        val profiles = mProfiles!!
        if (profiles.size > 1) {
            val items = ArrayList<ProfileListItem>()
            for (m in profiles) {
                val id = m.id ?: 0
                mProfilesById[id] = m
                items.add(ProfileListItem(id, m.name.orEmpty(), m.host.orEmpty(), false))
            }
            mListState.replaceAll(items)
        } else {
            if (profiles.size == 1) {
                playOnDream(profiles[0])
            } else {
                showToast(getString(R.string.no_profile_available))
            }
        }
    }

    fun execSimpleResultTask(params: ArrayList<NameValuePair>) {
        mSimpleResultJob?.cancel(null)
        mListState.progress = IndeterminateProgressState(
            title = getString(R.string.loading),
            message = getString(R.string.loading),
        )
        val handler = SimpleResultRequestHandler(URIStore.MEDIA_PLAYER_PLAY)
        mSimpleResultJob = launchSimpleResultLoad(handler, params) { _, result, http ->
            mSimpleResultJob = null
            onSimpleResult(true, result, http)
        }
    }

    fun onSimpleResult(success: Boolean, result: ExtendedHashMap?, http: SimpleHttpClient) {
        mListState.progress = null

        if (mTitle == null) mTitle = "..."
        var toastText = getString(R.string.sent_as, mTitle)
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
        @JvmField
        var LOG_TAG: String = ShareActivity::class.java.simpleName
    }
}
