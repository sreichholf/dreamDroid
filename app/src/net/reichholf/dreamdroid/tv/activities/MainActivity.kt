package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import javax.net.ssl.HttpsURLConnection
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.tv.ui.TvComposeHubHost

/**
 * Created by Stephan on 16.10.2016.
 *
 * Kotlin port of the TV browse host activity (Compose hub via [TvComposeHubHost]).
 */
class MainActivity : AppCompatActivity() {
    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this) {
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        localNetworkPermissionRequest.ensure(this)
        // Phase 3.1c-iv-f: Compose hub is the TV browse host (Leanback browse removed).
        TvComposeHubHost.install(this)
        try {
            HttpsURLConnection.setFollowRedirects(false)
            // Coil ImageLoader w/ OkHttpClient. Do not mutate process-wide
            // HttpsURLConnection defaults; trust-all is per OkHttp client.
            PiconImageLoader.install(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
