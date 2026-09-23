package net.reichholf.dreamdroid.ui.zap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.MenuProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.video.startLiveServiceStream

/**
 * Phase 2.7d: Zap channel grid as a direct Compose NavHost destination.
 * List, saved bouquet, and load/zap jobs live on [ZapViewModel].
 * Bouquet pick results arrive via [PhoneNavHandle.composeActivityResultListener].
 */
@Composable
fun ZapDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ZapViewModel = viewModel()
) {
    val context = LocalContext.current
    val title = viewModel.toolbarTitle
    val error = viewModel.errorText

    DisposableEffect(handle, viewModel) {
        val listener = ZapPickerResultForwarder(viewModel)
        val menuProvider = ZapMenuProvider(viewModel)
        handle.composeActivityResultListener = listener
        handle.dispatchPendingComposeActivityResult()
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(menuProvider)
        onDispose {
            if (handle.composeActivityResultListener === listener) {
                handle.composeActivityResultListener = null
            }
            activity?.removeMenuProvider(menuProvider)
        }
    }

    LaunchedEffect(title) {
        (context as? AppCompatActivity)?.title = title
    }
    LaunchedEffect(error) {
        if (!error.isNullOrEmpty()) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
    LaunchedEffect(handle, viewModel) {
        viewModel.pickBouquetRequests.collect { requestCode ->
            handle.navigateToPickBouquet(requestCode)
        }
    }
    LaunchedEffect(handle, viewModel, context) {
        viewModel.streamRequests.collect { service ->
            handle.runOnlineOnly {
                handle.lifecycleOwner.startLiveServiceStream(context, service.reference) {
                    try {
                        val activity = context as AppCompatActivity
                        activity.startActivity(
                            IntentFactory.getStreamServiceIntent(
                                activity,
                                service.reference,
                                service.name
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        viewModel.reportMissingStreamPlayer()
                    }
                }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.start()
    }

    DreamDroidPullRefresh(
        refreshing = viewModel.refresh.isRefreshing,
        onRefresh = { viewModel.reload() },
        enabled = viewModel.refresh.enabled,
        modifier = modifier
    ) {
        ZapScreen(
            items = viewModel.listState.items,
            gridState = viewModel.listState.gridState,
            scrollEpoch = viewModel.listState.scrollEpoch,
            emptyMessage = viewModel.emptyMessage,
            onItemClick = { service: Service ->
                handle.runOnlineOnly { viewModel.zapTo(service.reference) }
            },
            onItemLongClick = { service: Service -> viewModel.requestStream(service) }
        )
    }
}

private class ZapMenuProvider(private val viewModel: ZapViewModel) : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.epgbouquet, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.menu_pick_bouquet) {
            viewModel.pickBouquet()
            return true
        }
        return false
    }
}

private class ZapPickerResultForwarder(private val viewModel: ZapViewModel) :
    PhoneNavHandle.ActivityResultListener {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModel.onPickerResult(requestCode, resultCode, data)
    }
}
