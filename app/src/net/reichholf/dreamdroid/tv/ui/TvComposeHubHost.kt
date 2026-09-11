package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.BuildConfig

/**
 * Phase 3.1c-iv-b beachhead: empty Compose TV hub host behind a **debug-only** pref.
 * Default remains Leanback [net.reichholf.dreamdroid.tv.fragment.RootBrowseFragment].
 * Release builds always use Leanback regardless of the pref value.
 */
object TvComposeHubHost {
    const val PREFS_KEY_COMPOSE_TV_HUB: String = "compose_tv_hub_debug"

    @JvmStatic
    fun useComposeHub(context: Context): Boolean {
        if (!BuildConfig.DEBUG) {
            return false
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREFS_KEY_COMPOSE_TV_HUB, false)
    }

    @JvmStatic
    fun install(activity: ComponentActivity) {
        activity.setContent {
            ComposeTvHubStub()
        }
    }
}

/** Empty stub chrome so `androidx.tv` compiles and can be toggled in debug. */
@Composable
fun ComposeTvHubStub() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
                .testTag("compose_tv_hub_stub"),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Compose TV hub (stub)")
        }
    }
}
