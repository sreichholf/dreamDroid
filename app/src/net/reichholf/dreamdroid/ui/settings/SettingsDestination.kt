package net.reichholf.dreamdroid.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Phase 2.7e: Settings as a direct Compose NavHost destination.
 */
@Composable
fun SettingsDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember {
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
        SettingsState.create(context)
    }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        (context as? AppCompatActivity)?.title = context.getString(R.string.settings)
        onDispose { }
    }

    fun runMultiEpgSyncTest() {
        val profile = DreamDroid.getCurrentProfile()
        val bouquet = profile.defaultBouquetTv?.takeIf { it.isNotBlank() }
        if (bouquet == null) {
            Toast.makeText(
                context,
                R.string.multiepg_sync_test_no_bouquet,
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        Toast.makeText(
            context,
            R.string.multiepg_sync_test_running,
            Toast.LENGTH_SHORT,
        ).show()
        scope.launch {
            val started = System.currentTimeMillis()
            val message = try {
                // ttlMs=0 forces a network fetch every tap (useful for box load checks).
                val sync = MultiEpgSync(
                    dao = AppDatabase.epg(context),
                    fetch = MultiEpgSync.httpFetch(),
                    ttlMs = 0L,
                )
                val events = sync.ensureChunk(
                    profileId = profile.getId(),
                    bouquetRef = bouquet,
                    unixSec = System.currentTimeMillis() / 1000L,
                )
                val ms = System.currentTimeMillis() - started
                context.getString(R.string.multiepg_sync_test_ok, events.size, ms)
            } catch (t: Throwable) {
                context.getString(
                    R.string.multiepg_sync_test_fail,
                    t.message ?: t.javaClass.simpleName,
                )
            }
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsScreen(
        state = state,
        onThemeChanged = {
            DreamDroid.setTheme(context as AppCompatActivity)
        },
        onDynamicColorsChanged = {
            if (DynamicColors.isDynamicColorAvailable()) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { DreamDroid.restart(context) },
                    300,
                )
            }
        },
        onSyncPicons = {
            (context as? BaseActivity)?.startPiconSync()
        },
        // TEMP MultiEPG Phase 1 — remove with Phase 2 UI.
        onMultiEpgSyncTest = { runMultiEpgSyncTest() },
        onAbout = {
            // Phase 2.1g-ii-b: Navigation Compose dialog (no DialogFragment).
            hostFragment.navigateToAbout()
        },
        onChangelog = {
            (context as? MainActivity)?.showChangeLog(false)
        },
        onBackup = {
            hostFragment.navigateToBackup()
        },
        modifier = modifier,
    )
}
