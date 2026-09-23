/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.ui.share.ShareProfilesHost
import net.reichholf.dreamdroid.ui.share.ShareRequest
import net.reichholf.dreamdroid.ui.share.ShareViewModel
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Share / view intent → pick a profile (Compose) → play on the box via MEDIA_PLAYER_PLAY.
 */
class ShareActivity : AppCompatActivity() {
    private val viewModel: ShareViewModel by viewModels()
    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        localNetworkPermissionRequest.ensure(this)
        title = getText(R.string.watch_on_dream)
        setContent {
            DreamDroidTheme {
                ShareProfilesHost(
                    title = stringResource(R.string.watch_on_dream),
                    state = viewModel.listState,
                    onProfileClick = viewModel::onProfileClick
                )
            }
            val toast = viewModel.toast
            LaunchedEffect(toast) {
                if (toast != null) {
                    Toast.makeText(applicationContext, toast, Toast.LENGTH_LONG).show()
                    viewModel.consumeToast()
                }
            }
            val finished = viewModel.finished
            LaunchedEffect(finished) {
                if (finished) {
                    finish()
                }
            }
        }
        viewModel.start(shareRequest(intent))
    }

    private fun shareRequest(intent: Intent): ShareRequest {
        val extras = intent.extras
        val url = when (intent.action) {
            Intent.ACTION_SEND -> extras?.getString(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        // semperVidLinks sends "artist" and "song" for YouTube video titles.
        val song = extras?.getString("song")
        val title = if (song != null) {
            extras.getString("artist")?.let { artist -> "$artist - $song" }
        } else {
            extras?.getString("title")
        }
        return ShareRequest(url = url, title = title)
    }
}
