package net.reichholf.dreamdroid.ui.settings

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.enigma.toEnigmaDisplayMessage
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.multiepg.UserBouquetEpgFill
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Phase 2.7e: Settings as a direct Compose NavHost destination.
 */
@Composable
fun SettingsDestination(handle: PhoneNavHandle, modifier: Modifier = Modifier) {
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
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Toast.makeText(
            context,
            R.string.multiepg_sync_test_running,
            Toast.LENGTH_SHORT
        ).show()
        scope.launch {
            val started = System.currentTimeMillis()
            val message = try {
                val profileId = profile.id ?: -1
                val events = UserBouquetEpgFill.ensureNowChunk(
                    sync = MultiEpgSyncHolder.shared(context),
                    rosterDao = AppDatabase.roster(context),
                    profileId = profileId,
                    containerRef = bouquet,
                    tabRootRef = bouquet,
                    excludedTabRefs = UserBouquetCache.excludedHubTabRefs(context),
                    unixSec = System.currentTimeMillis() / 1000L,
                    forceRefresh = true
                )
                val ms = System.currentTimeMillis() - started
                if (events.isEmpty()) {
                    context.getString(
                        R.string.multiepg_sync_test_empty,
                        bouquet.take(48),
                        ms
                    )
                } else {
                    context.getString(R.string.multiepg_sync_test_ok, events.size, ms)
                }
            } catch (t: Throwable) {
                context.getString(
                    R.string.multiepg_sync_test_fail,
                    t.toEnigmaDisplayMessage(context)
                )
            }
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun resetUseDrivenCache(allProfiles: Boolean) {
        scope.launch {
            val db = AppDatabase.database(context)
            if (allProfiles) {
                UseDrivenCache.clearAll(db)
            } else {
                val profileId = DreamDroid.getCurrentProfile().id
                if (profileId != null) {
                    UseDrivenCache.clearForProfile(db, profileId)
                }
            }
            SessionConnectionHolder.shared.onUseDrivenCacheCleared()
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(context, R.string.reset_cache_done, Toast.LENGTH_SHORT).show()
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
                    300
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
            handle.navigateToAbout()
        },
        onChangelog = {
            (context as? MainActivity)?.showChangeLog(false)
        },
        onBackup = {
            handle.navigateToBackup()
        },
        onResetCache = { allProfiles -> resetUseDrivenCache(allProfiles) },
        modifier = modifier
    )
}
