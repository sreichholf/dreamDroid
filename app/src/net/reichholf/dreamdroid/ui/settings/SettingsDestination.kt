package net.reichholf.dreamdroid.ui.settings

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.color.DynamicColors
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

/**
 * Phase 2.7e: Settings as a direct Compose NavHost destination.
 * Preference reads live on [SettingsViewModel].
 */
@Composable
fun SettingsDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val message = viewModel.message
    val title = stringResource(R.string.settings)
    DisposableEffect(Unit) {
        (context as? AppCompatActivity)?.title = title
        onDispose { }
    }
    LaunchedEffect(message) {
        if (!message.isNullOrEmpty()) {
            Toast.makeText(context, message, viewModel.messageDuration).show()
            viewModel.consumeMessage()
        }
    }

    SettingsScreen(
        state = viewModel.state,
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
        onResetCache = { allProfiles -> viewModel.resetUseDrivenCache(allProfiles) },
        modifier = modifier
    )
}
