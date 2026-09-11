package net.reichholf.dreamdroid.ui.epg

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val LOG_TAG = "EpgBouquetDestination"

/**
 * Phase 2.7f: bouquet EPG as a direct Compose NavHost destination.
 * Date/time header mounts into activity [R.id.content_header].
 * Bouquet pick results arrive via [PhoneNavHostFragment.composeActivityResultListener].
 */
@Composable
fun EpgBouquetDestination(
    hostFragment: PhoneNavHostFragment,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    val leafArgs = hostFragment.epgLeafArguments()
    var bouquetRef by rememberSaveable(remountEpoch) {
        mutableStateOf(leafArgs.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE).orEmpty())
    }
    var bouquetName by rememberSaveable(remountEpoch) {
        mutableStateOf(leafArgs.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME).orEmpty())
    }
    val nowSec = (Calendar.getInstance().timeInMillis / 1000).toInt()
    var timeSec by rememberSaveable(remountEpoch) { mutableIntStateOf(nowSec) }
    if (timeSec < nowSec) {
        timeSec = nowSec
    }
    val listState = remember { EpgBouquetListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var waitingForPicker by rememberSaveable { mutableStateOf(false) }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    val session = remember { EpgBouquetSession() }
    session.hostFragment = hostFragment
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

    DisposableEffect(hostFragment, session, dialogSession, remountEpoch) {
        hostFragment.composeDialogActionListener = dialogSession
        hostFragment.composeActivityResultListener = session
        activity.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        session.setToolbarTitle(session.finishedTitle())

        val header = LayoutInflater.from(activity).inflate(R.layout.date_time_picker_header, null, false)
        val dateView = header.findViewById<TextView>(R.id.textViewDate)
        val timeView = header.findViewById<TextView>(R.id.textViewTime)
        fun syncHeaderLabels() {
            val cal = session.calendar()
            dateView.text = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            timeView.text = SimpleDateFormat("HH:mm", Locale.US).format(cal.time)
        }
        syncHeaderLabels()
        dateView.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(session.calendar().timeInMillis)
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                val newDate = Calendar.getInstance().apply { timeInMillis = selection as Long }
                session.onDateSet(
                    newDate.get(Calendar.YEAR),
                    newDate.get(Calendar.MONTH),
                    newDate.get(Calendar.DAY_OF_MONTH),
                )
                syncHeaderLabels()
            }
            (activity as MultiPaneHandler).showDialogFragment(picker, "epg_bouquet_date_picker")
        }
        timeView.setOnClickListener {
            val c = session.calendar()
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(c.get(Calendar.HOUR_OF_DAY))
                .setMinute(c.get(Calendar.MINUTE))
                .build()
            picker.addOnPositiveButtonClickListener {
                session.onTimeSet(picker.hour, picker.minute)
                syncHeaderLabels()
            }
            (activity as MultiPaneHandler).showDialogFragment(picker, "epg_bouquet_time_picker")
        }
        val frame = activity.findViewById<FrameLayout?>(R.id.content_header)
        frame?.visibility = View.VISIBLE
        frame?.removeAllViews()
        frame?.addView(header)

        onDispose {
            if (hostFragment.composeDialogActionListener === dialogSession) {
                hostFragment.composeDialogActionListener = null
            }
            if (hostFragment.composeActivityResultListener === session) {
                hostFragment.composeActivityResultListener = null
            }
            activity.removeMenuProvider(session)
            loadJob?.cancel()
            loadJob = null
            dialogSession.dismissProgress()
            frame?.removeAllViews()
            frame?.visibility = View.GONE
        }
    }

    LaunchedEffect(remountEpoch, bouquetRef) {
        val args = hostFragment.epgLeafArguments()
        val ref = args.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE).orEmpty()
        val name = args.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME).orEmpty()
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
        modifier = modifier,
    ) {
        EpgBouquetScreen(
            items = listState.items,
            listState = listState.listState,
            scrollEpoch = listState.scrollEpoch,
            emptyMessage = emptyMessage,
            onItemClick = { dialogSession.showDetail(it) },
        )
    }
}

private class EpgBouquetSession :
    PhoneNavHostFragment.ActivityResultListener,
    MenuProvider {
    var hostFragment: PhoneNavHostFragment? = null
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

    fun calendar(): Calendar = Calendar.getInstance().apply {
        timeInMillis = timeSec.toLong() * 1000
    }

    fun onDateSet(year: Int, month: Int, day: Int) {
        val cal = calendar()
        if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DATE) == day) {
            return
        }
        cal.set(year, month, day)
        timeSec = (cal.timeInMillis / 1000).toInt()
        onTimeSec?.invoke(timeSec)
        Log.i(LOG_TAG, "$timeSec")
        reload()
    }

    fun onTimeSet(hourOfDay: Int, minute: Int) {
        val cal = calendar()
        if (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && cal.get(Calendar.MINUTE) == minute) {
            return
        }
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        timeSec = (cal.timeInMillis / 1000).toInt()
        onTimeSec?.invoke(timeSec)
        Log.i(LOG_TAG, "$timeSec")
        reload()
    }

    fun reload() {
        val host = hostFragment ?: return
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
                    NameValuePair("time", timeSec.toString()),
                ),
                URIStore.EPG_BOUQUET,
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
        val service = data?.getSerializableExtra(KEY_BOUQUET) as? ExtendedHashMap ?: return
        val reference = service.getString(Service.KEY_REFERENCE).orEmpty()
        if (reference != bouquetRef) {
            bouquetRef = reference
            bouquetName = service.getString(Service.KEY_NAME).orEmpty()
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
            val host = hostFragment ?: return true
            waitingForPicker = true
            onWaitingForPicker?.invoke(true)
            host.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)
            return true
        }
        return false
    }
}
