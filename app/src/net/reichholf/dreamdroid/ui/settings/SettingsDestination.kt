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
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.about.AboutComposeDialog

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

    DisposableEffect(Unit) {
        (context as? AppCompatActivity)?.title = context.getString(R.string.settings)
        onDispose { }
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
        onAbout = {
            (context as? MultiPaneHandler)?.showDialogFragment(
                AboutComposeDialog.newInstance(),
                "about_dialog",
            )
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
