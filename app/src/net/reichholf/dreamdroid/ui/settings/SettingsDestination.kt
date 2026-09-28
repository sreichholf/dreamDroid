package net.reichholf.dreamdroid.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.delay
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/** Settings as a NavHost destination. Theme changes and restarts run here, on the activity. */
@Composable
fun SettingsDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    LaunchedEffect(uiState.effect) {
        when (uiState.effect) {
            SettingsEffect.ApplyTheme -> (context as? AppCompatActivity)?.let(DreamDroid::setTheme)

            SettingsEffect.Restart -> if (DynamicColors.isDynamicColorAvailable()) {
                delay(RESTART_DELAY_MS)
                DreamDroid.restart(context)
            }

            null -> return@LaunchedEffect
        }
        viewModel.onEffectHandled()
    }

    SettingsScreen(
        settings = uiState.settings,
        onChange = viewModel::update,
        onSyncPicons = {
            (context as? BaseActivity)?.startPiconSync()
        },
        showDeveloperCategory = context.isDebuggable(),
        showDynamicThemeColors = DynamicColors.isDynamicColorAvailable(),
        syncPiconsPathDraft = viewModel.syncPiconsPath.state.takeIf {
            uiState.editingSyncPiconsPath
        },
        onEditSyncPiconsPath = viewModel::editSyncPiconsPath,
        onConfirmSyncPiconsPath = viewModel::confirmSyncPiconsPath,
        onDismissSyncPiconsPath = viewModel::dismissSyncPiconsPath,
        onAbout = handle::navigateToAbout,
        onChangelog = {
            (context as? MainActivity)?.showChangeLog(false)
        },
        onBackup = handle::navigateToBackup,
        onResetCache = viewModel::resetCache,
        modifier = modifier
    )
}

/** The developer settings show in debuggable builds only. */
internal fun Context.isDebuggable(): Boolean =
    0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)

private const val RESTART_DELAY_MS = 300L
