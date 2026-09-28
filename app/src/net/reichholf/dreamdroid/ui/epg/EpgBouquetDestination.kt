package net.reichholf.dreamdroid.ui.epg

import android.app.Activity
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.Epg
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET
import net.reichholf.dreamdroid.ui.text.asString

/**
 * Bouquet list EPG as a NavHost destination. Time jump is date/time chips + Now/Prime;
 * each chip opens a stock Material picker. Bouquet identity, the list, and the load live
 * on [EpgBouquetViewModel]. Bouquet pick results arrive via
 * [PhoneNavHandle.composeActivityResultListener].
 */
@Composable
fun EpgBouquetDestination(
    handle: PhoneNavHandle,
    route: Epg,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: EpgBouquetViewModel = hiltViewModel(),
    detailViewModel: EpgEventDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    ShellTitle(uiState.title)
    val nowSec = { (System.currentTimeMillis() / 1000L).toInt() }
    val openMultiEpg = { atSec: Long ->
        openBouquetMultiEpg(context, handle, uiState.bouquetRef, uiState.bouquetName, atSec)
    }
    BindShellTopBarActions(
        epgBouquetTopBarActions(
            bouquetRef = uiState.bouquetRef,
            multiEpgLabel = stringResource(R.string.multiepg),
            pickBouquetLabel = stringResource(R.string.bouquet_overview),
            onOpenMultiEpg = { openMultiEpg((uiState.timeSec ?: nowSec()).toLong()) },
            onPickBouquet = viewModel::pickBouquet
        )
    )

    DisposableEffect(handle, viewModel) {
        val pickerListener = PhoneNavHandle.ActivityResultListener { request, result, data ->
            val bouquet = data?.getSerializableExtraCompat<Service>(KEY_BOUQUET)
            if (result == Activity.RESULT_OK &&
                request == Statics.REQUEST_PICK_BOUQUET &&
                bouquet != null
            ) {
                viewModel.onBouquetPicked(bouquet.reference, bouquet.name)
            }
        }
        handle.composeActivityResultListener = pickerListener
        handle.dispatchPendingComposeActivityResult()
        onDispose {
            if (handle.composeActivityResultListener === pickerListener) {
                handle.composeActivityResultListener = null
            }
        }
    }

    LaunchedEffect(uiState.openPicker) {
        if (uiState.openPicker) {
            viewModel.onPickerOpened()
            handle.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)
        }
    }
    LaunchedEffect(uiState.scrollToTop) {
        if (uiState.scrollToTop) {
            listState.scrollToItem(0)
            viewModel.onScrolledToTop()
        }
    }
    LaunchedEffect(remountEpoch, route) {
        viewModel.onShown(route, remountEpoch, nowSec())
    }

    val labelLocale =
        if (DreamDroid.DATE_LOCALE_WO) Locale.US else LocalLocale.current.platformLocale
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeSec = uiState.timeSec ?: nowSec()
    val timeJump = EpgTimeJumpUi(
        dateLabel = EpgInstant.formatDateLabel(timeSec, labelLocale),
        timeLabel = EpgInstant.formatTimeLabel(timeSec, is24Hour, labelLocale),
        onPickDate = { showDatePicker = true },
        onPickTime = { showTimePicker = true },
        onNow = { viewModel.onInstantSet(nowSec()) },
        onPrime = { viewModel.onInstantSet(EpgInstant.primeTimeSec()) },
        onTimeline = { openMultiEpg(timeSec.toLong()) }
    )

    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = { viewModel.reload(forceRefresh = true) },
        enabled = true,
        modifier = modifier
    ) {
        EpgBouquetScreen(
            items = uiState.events,
            listState = listState,
            emptyMessage = uiState.emptyMessage?.asString(),
            bouquetPick = EpgBouquetPickUi(
                bouquetName = uiState.bouquetName,
                onPickBouquet = viewModel::pickBouquet
            ),
            timeJump = timeJump,
            onItemClick = detailViewModel::showDetail
        )
    }

    if (showDatePicker) {
        EpgDatePickerDialog(
            initialTimeSec = timeSec,
            onDismiss = { showDatePicker = false },
            onConfirm = { utcDateMillis ->
                showDatePicker = false
                viewModel.onInstantSet(EpgInstant.applyDate(timeSec, utcDateMillis))
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
                viewModel.onInstantSet(EpgInstant.applyTime(timeSec, hour, minute))
            }
        )
    }

    EpgEventDetailHost(handle, detailViewModel)
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
