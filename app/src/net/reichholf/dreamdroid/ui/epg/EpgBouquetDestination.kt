package net.reichholf.dreamdroid.ui.epg

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Locale
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.Epg
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Phase 2.7f: bouquet EPG as a direct Compose NavHost destination.
 * Time jump is date/time chips + Now/Prime; each chip opens a stock Material picker.
 * Bouquet identity, the list, and the load job live on [EpgBouquetViewModel].
 * Bouquet pick results arrive via [PhoneNavHandle.composeActivityResultListener].
 */
@Composable
fun EpgBouquetDestination(
    handle: PhoneNavHandle,
    route: Epg,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: EpgBouquetViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.handle = handle
    dialogSession.context = context
    BindShellTopBarActions(
        epgBouquetTopBarActions(
            bouquetRef = viewModel.bouquetRef,
            multiEpgLabel = stringResource(R.string.multiepg),
            pickBouquetLabel = stringResource(R.string.bouquet_overview),
            onOpenMultiEpg = {
                val atSec = viewModel.timeSec?.toLong()
                    ?: Calendar.getInstance().timeInMillis / 1000L
                openBouquetMultiEpg(
                    context,
                    handle,
                    viewModel.bouquetRef,
                    viewModel.bouquetName,
                    atSec
                )
            },
            onPickBouquet = { viewModel.pickBouquet() }
        )
    )
    val pickerListener = remember(viewModel) { EpgBouquetPickerForwarder(viewModel) }

    DisposableEffect(handle, pickerListener, dialogSession) {
        handle.composeActivityResultListener = pickerListener
        handle.dispatchPendingComposeActivityResult()
        onDispose {
            if (handle.composeActivityResultListener === pickerListener) {
                handle.composeActivityResultListener = null
            }
            dialogSession.dismissProgress()
        }
    }

    LaunchedEffect(viewModel, handle) {
        viewModel.pickBouquetRequests.collect { requestCode ->
            handle.navigateToPickBouquet(requestCode)
        }
    }

    val labelLocale =
        if (DreamDroid.DATE_LOCALE_WO) Locale.US else LocalLocale.current.platformLocale
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeSec = viewModel.timeSec
        ?: (Calendar.getInstance().timeInMillis / 1000L).toInt()
    val timeJump = EpgTimeJumpUi(
        dateLabel = EpgInstant.formatDateLabel(timeSec, labelLocale),
        timeLabel = EpgInstant.formatTimeLabel(timeSec, is24Hour, labelLocale),
        onPickDate = { showDatePicker = true },
        onPickTime = { showTimePicker = true },
        onNow = {
            viewModel.onInstantSet((Calendar.getInstance().timeInMillis / 1000).toInt())
        },
        onPrime = { viewModel.onInstantSet(EpgInstant.primeTimeSec()) },
        onTimeline = {
            openBouquetMultiEpg(
                context,
                handle,
                viewModel.bouquetRef,
                viewModel.bouquetName,
                viewModel.timeSec?.toLong() ?: timeSec.toLong()
            )
        }
    )

    val connectionSession =
        SessionConnectionHolder.shared.status.collectAsState().value.session
    LaunchedEffect(remountEpoch, connectionSession, viewModel, handle, route) {
        val leafRef = route.serviceRef
        val leafName = route.serviceName
        val leafTime = route.timeOrNull()
        val nowSec = (Calendar.getInstance().timeInMillis / 1000).toInt()
        viewModel.ensureEpoch(remountEpoch, leafRef, leafName, leafTime, nowSec)
        viewModel.applyLeaf(leafRef, leafName)
        viewModel.reload()
    }

    val toolbarTitle = if (viewModel.refresh.isRefreshing) {
        stringResource(R.string.loading)
    } else {
        viewModel.bouquetName.takeIf { it.isNotEmpty() } ?: stringResource(R.string.epg)
    }
    LaunchedEffect(toolbarTitle) {
        activity.title = toolbarTitle
    }

    DreamDroidPullRefresh(
        refreshing = viewModel.refresh.isRefreshing,
        onRefresh = { viewModel.reload(forceRefresh = true) },
        enabled = viewModel.refresh.enabled,
        modifier = modifier
    ) {
        EpgBouquetScreen(
            items = viewModel.listState.items,
            listState = viewModel.listState.listState,
            scrollEpoch = viewModel.listState.scrollEpoch,
            emptyMessage = viewModel.emptyMessage,
            bouquetPick = EpgBouquetPickUi(
                bouquetName = viewModel.bouquetName,
                onPickBouquet = { viewModel.pickBouquet() }
            ),
            timeJump = timeJump,
            onItemClick = { dialogSession.showDetail(it) }
        )
    }

    if (showDatePicker) {
        EpgDatePickerDialog(
            initialTimeSec = timeSec,
            onDismiss = { showDatePicker = false },
            onConfirm = { utcDateMillis ->
                showDatePicker = false
                viewModel.onInstantSet(
                    EpgInstant.applyDate(viewModel.timeSec ?: timeSec, utcDateMillis)
                )
            }
        )
    }
    if (showTimePicker) {
        EpgTimePickerDialog(
            initialTimeSec = timeSec,
            is24Hour = is24Hour,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                viewModel.onInstantSet(
                    EpgInstant.applyTime(viewModel.timeSec ?: timeSec, hour, minute)
                )
            }
        )
    }

    EpgEventDetailSheetHost(dialogSession)
}

internal fun openBouquetMultiEpg(
    context: Context,
    handle: PhoneNavHandle?,
    bouquetRef: String,
    bouquetName: String,
    timeSec: Long
) {
    if (bouquetRef.isEmpty()) {
        return
    }
    DrawerEpgMode.saveMulti(context)
    handle?.navigateToMultiEpg(bouquetRef, bouquetName, timeSec = timeSec)
}

/** MultiEPG jump (once a bouquet is set) and the bouquet picker. */
internal fun epgBouquetTopBarActions(
    bouquetRef: String,
    multiEpgLabel: String,
    pickBouquetLabel: String,
    onOpenMultiEpg: () -> Unit,
    onPickBouquet: () -> Unit
): List<ShellTopBarAction> = buildList {
    if (bouquetRef.isNotEmpty()) {
        add(
            ShellTopBarAction(
                id = R.id.menu_multiepg,
                label = multiEpgLabel,
                iconRes = R.drawable.ic_multiepg,
                onClick = onOpenMultiEpg
            )
        )
    }
    add(
        ShellTopBarAction(
            id = R.id.menu_pick_bouquet,
            label = pickBouquetLabel,
            iconRes = R.drawable.ic_action_list,
            onClick = onPickBouquet
        )
    )
}

private class EpgBouquetPickerForwarder(private val viewModel: EpgBouquetViewModel) :
    PhoneNavHandle.ActivityResultListener {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModel.onPickerResult(requestCode, resultCode, data)
    }
}
