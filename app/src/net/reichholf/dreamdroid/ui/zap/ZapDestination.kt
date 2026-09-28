package net.reichholf.dreamdroid.ui.zap

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET
import net.reichholf.dreamdroid.ui.text.asString
import net.reichholf.dreamdroid.video.startLiveServiceStream

/**
 * The zap channel grid as a NavHost destination. Bouquet picker results arrive through
 * [PhoneNavHandle.composeActivityResultListener].
 */
@Composable
fun ZapDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ZapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    DisposableEffect(handle, viewModel) {
        val listener = PhoneNavHandle.ActivityResultListener { requestCode, resultCode, data ->
            if (requestCode != Statics.REQUEST_PICK_BOUQUET) {
                return@ActivityResultListener
            }
            if (resultCode == Activity.RESULT_OK) {
                viewModel.onBouquetPicked(
                    data?.getSerializableExtraCompat<Service>(KEY_BOUQUET) ?: Service("", "")
                )
            } else {
                viewModel.onBouquetPickCancelled()
            }
        }
        handle.composeActivityResultListener = listener
        handle.dispatchPendingComposeActivityResult()
        onDispose {
            if (handle.composeActivityResultListener === listener) {
                handle.composeActivityResultListener = null
            }
        }
    }
    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = R.id.menu_pick_bouquet,
                label = stringResource(R.string.bouquet_overview),
                iconRes = R.drawable.ic_action_list,
                onClick = viewModel::pickBouquet
            )
        )
    )

    val effect = uiState.effect
    LaunchedEffect(effect) {
        when (effect) {
            null -> return@LaunchedEffect

            ZapEffect.PickBouquet -> handle.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)

            is ZapEffect.Stream -> handle.runOnlineOnly {
                val service = effect.service
                handle.lifecycleOwner.startLiveServiceStream(context, service.reference) {
                    try {
                        context.startActivity(
                            IntentFactory.getStreamServiceIntent(
                                context,
                                service.reference,
                                service.name
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onStreamFailed()
                    }
                }
            }
        }
        viewModel.onEffectHandled()
    }

    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = viewModel::reload,
        enabled = true,
        modifier = modifier
    ) {
        ZapScreen(
            items = uiState.items,
            gridState = gridState,
            scrollEpoch = uiState.scrollEpoch,
            emptyMessage = uiState.emptyMessage?.asString(),
            zapBlocked = uiState.zapBlocked,
            onItemClick = { service -> handle.runOnlineOnly { viewModel.zap(service) } },
            onItemLongClick = viewModel::stream
        )
    }
}
