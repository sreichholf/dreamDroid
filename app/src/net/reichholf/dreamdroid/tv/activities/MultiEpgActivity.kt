package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.tv.ui.TvMultiEpgHost

/** GraphMultiEPG grid for the Compose TV hub (Settings → MultiEPG). */
class MultiEpgActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        title = getString(R.string.multiepg)
        setContent {
            TvMultiEpgHost(activity = this)
        }
    }
}
