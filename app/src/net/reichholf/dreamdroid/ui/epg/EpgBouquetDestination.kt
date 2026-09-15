package net.reichholf.dreamdroid.ui.epg

import android.app.Activity
import android.content.Intent
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.MenuProvider
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET

/**
 * Phase 2.7f: bouquet EPG as a direct Compose NavHost destination.
 * Time jump is date/time chips + Now/Prime; each chip opens a stock Material picker.
 * Bouquet pick results arrive via [PhoneNavHandle.composeActivityResultListener].
 */
@Composable
fun EpgBouquetDestination(
    handle: PhoneNavHandle,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    val leafArgs = handle.epgLeafArguments()
    var bouquetRef by rememberSaveable(remountEpoch) {
        mutableStateOf(
            leafArgs.getString(
                net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE
            ).orEmpty()
        )
    }
    var bouquetName by rememberSaveable(remountEpoch) {
        mutableStateOf(
            leafArgs.getString(
                net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME
            ).orEmpty()
        )
    }
    val nowSec = (Calendar.getInstance().timeInMillis / 1000).toInt()
    var timeSec by rememberSaveable(remountEpoch) { mutableIntStateOf(nowSec) }
    val listState = remember { EpgBouquetListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var waitingForPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.handle = handle
    dialogSession.context = context

    val session = remember { EpgBouquetSession() }
    session.handle = handle
    session.context = context
    session.bouquetRef = bouquetRef
    session.bouquetName = bouquetName
    session.timeSec = timeSec
    session.waitingForPicker = waitingForPicker
    session.listState = listState
    session.refresh = refresh
    session.scope = scope
    session.onBouquetRef = { bouquetRef = it }
    session.onBouquetName = { bouquetName = it }
    session.onTimeSec = { timeSec = it }
    session.onWaitingForPicker = { waitingForPicker = it }
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }

    DisposableEffect(handle, session, dialogSession, remountEpoch) {
        handle.composeActivityResultListener = session
        activity.addMenuProvider(session)
        session.setToolbarTitle(session.finishedTitle())

        onDispose {
            if (handle.composeActivityResultListener === session) {
                handle.composeActivityResultListener = null
            }
            activity.removeMenuProvider(session)
            loadJob?.cancel()
            loadJob = null
            dialogSession.dismissProgress()
        }
    }

    val labelLocale = if (DreamDroid.DATE_LOCALE_WO) Locale.US else Locale.getDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeJump = EpgTimeJumpUi(
        dateLabel = EpgInstant.formatDateLabel(timeSec, labelLocale),
        timeLabel = EpgInstant.formatTimeLabel(timeSec, is24Hour, labelLocale),
        onPickDate = { showDatePicker = true },
        onPickTime = { showTimePicker = true },
        onNow = {
            session.onInstantSet((Calendar.getInstance().timeInMillis / 1000).toInt())
        },
        onPrime = { session.onInstantSet(EpgInstant.primeTimeSec()) }
    )

    LaunchedEffect(remountEpoch, bouquetRef) {
        val args = handle.epgLeafArguments()
        val ref = args.getString(
            net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE
        ).orEmpty()
        val name = args.getString(
            net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME
        ).orEmpty()
        if (ref.isNotEmpty() && ref != bouquetRef) {
            bouquetRef = ref
            bouquetName = name
            listState.scrollToTop()
        }
        session.reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { session.reload() },
        enabled = refresh.enabled,
        modifier = modifier
    ) {
        EpgBouquetScreen(
            items = listState.items,
            listState = listState.listState,
            scrollEpoch = listState.scrollEpoch,
            emptyMessage = emptyMessage,
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
                session.onInstantSet(EpgInstant.applyDate(timeSec, utcDateMillis))
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
                session.onInstantSet(EpgInstant.applyTime(timeSec, hour, minute))
            }
        )
    }

    EpgEventDetailSheetHost(dialogSession)
}

private class EpgBouquetSession :
    PhoneNavHandle.ActivityResultListener,
    MenuProvider {
    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null
    var bouquetRef: String = ""
    var bouquetName: String = ""
    var timeSec: Int = 0
    var waitingForPicker: Boolean = false
    var listState: EpgBouquetListState? = null
    var refresh: ComposeRefreshState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var onBouquetRef: ((String) -> Unit)? = null
    var onBouquetName: ((String) -> Unit)? = null
    var onTimeSec: ((Int) -> Unit)? = null
    var onWaitingForPicker: ((Boolean) -> Unit)? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    private var loadJob: Job? = null

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun finishedTitle(): String {
        val ctx = context ?: return ""
        return bouquetName.takeIf { it.isNotEmpty() } ?: ctx.getString(R.string.epg)
    }

    fun onInstantSet(newTimeSec: Int) {
        if (newTimeSec == timeSec) {
            return
        }
        timeSec = newTimeSec
        onTimeSec?.invoke(timeSec)
        reload()
    }

    fun reload() {
        val host = handle ?: return
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (bouquetRef.isEmpty() && !waitingForPicker) {
            waitingForPicker = true
            onWaitingForPicker?.invoke(true)
            host.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)
            return
        }
        if (bouquetRef.isEmpty()) {
            return
        }
        if (state.items.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            val result = loadEventList(
                ctx.applicationContext,
                listOf(
                    NameValuePair("bRef", bouquetRef),
                    NameValuePair("time", timeSec.toString())
                ),
                URIStore.EPG_BOUQUET
            )
            refreshState.setRefreshing(false)
            setToolbarTitle(finishedTitle())
            if (!result.success) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(result.errorText)
                return@launch
            }
            if (result.events.isEmpty()) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
            } else {
                onEmptyMessage?.invoke(null)
                state.replaceAll(result.events)
            }
        }
        onLoadJob?.invoke(loadJob)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || requestCode != Statics.REQUEST_PICK_BOUQUET) {
            return
        }
        @Suppress("DEPRECATION")
        val service = data?.getSerializableExtra(KEY_BOUQUET) as? Service ?: return
        val reference = service.reference
        if (reference != bouquetRef) {
            bouquetRef = reference
            bouquetName = service.name
            onBouquetRef?.invoke(bouquetRef)
            onBouquetName?.invoke(bouquetName)
            listState?.scrollToTop()
        }
        waitingForPicker = false
        onWaitingForPicker?.invoke(false)
        reload()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.epgbouquet, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.menu_pick_bouquet) {
            val host = handle ?: return true
            waitingForPicker = true
            onWaitingForPicker?.invoke(true)
            host.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)
            return true
        }
        return false
    }
}
