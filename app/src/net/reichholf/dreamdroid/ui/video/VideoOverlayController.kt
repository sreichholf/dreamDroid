package net.reichholf.dreamdroid.ui.video

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import java.io.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie as EnigmaMovie
import net.reichholf.dreamdroid.enigma.Service as BouquetService
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.getSerializableCompat
import net.reichholf.dreamdroid.helpers.isTelevision
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.ui.allowsStreaming
import net.reichholf.dreamdroid.tv.ui.bindTvChannelList
import net.reichholf.dreamdroid.tv.ui.bindTvPlayerInfobar
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.video.MediaTrack
import net.reichholf.dreamdroid.video.PlayerEvent
import net.reichholf.dreamdroid.video.VideoPlayback
import net.reichholf.dreamdroid.video.VideoPlayer

/**
 * Player overlay chrome hosted by [VideoActivity]. Not a Fragment — inflate into
 * [R.id.overlay], wire Compose / gestures, and drive playback chrome.
 */
class VideoOverlayController(
    private val activity: VideoActivity,
    private val playback: VideoPlaybackViewModel,
    private val sessions: SessionConnectionHolder,
    private val player: VideoPlayer
) : DialogActionListener {

    private var attached: Boolean = false
    private var resumed: Boolean = false
    private var rootView: View? = null

    private var surfaceHeight: Int = 0
    private var surfaceWidth: Int = 0

    private val session: VideoPlaybackUiState get() = playback.uiState.value
    private val movie: EnigmaMovie? get() = session.movie
    private val currentService: ServiceNowNext? get() = session.currentService

    private lateinit var handler: Handler
    private lateinit var autoHideRunnable: Runnable
    private lateinit var issueReloadRunnable: Runnable

    private val tvOverlay: Boolean = activity.isTelevision()
    private val channelSwipe = VideoChannelSwipe()

    private var overlayRoot: View? = null
    private var composeOverlay: ComposeView? = null
    private var composeZapList: ComposeView? = null
    private var composeBouquetBar: ComposeView? = null

    private val overlayUiState: VideoOverlayUiState = VideoOverlayUiState()

    private var gestureDetector: GestureDetector? = null
    private lateinit var audioManager: AudioManager
    private var audioMaxVol: Int = 0
    private var volume: Float = 0f
    private var servicesViewVisible: Boolean = false

    private var playbackJob: Job? = null
    private var playerStateJob: Job? = null
    private var renderedEventKey: String? = null
    private var reloadSchedule: EventReloadSchedule? = null
    private var reloadDueAtMs: Long? = null
    private var tvSessionJob: Job? = null
    private var tvChannelListBound: Boolean = false
    private var phoneZapListBound: Boolean = false
    private var savedScreenBrightness: Float? = null
    private var backCallback: OnBackPressedCallback? = null

    fun attach(extras: Bundle?) {
        if (attached) {
            applyPlaybackExtras(extras)
            return
        }
        handler = Handler(Looper.getMainLooper())
        servicesViewVisible = false
        reloadDueAtMs = null
        autoHideRunnable = Runnable { hideOverlays() }
        issueReloadRunnable = Runnable {
            // Paused: keep the due time; onResume re-arms it without a retry.
            if (attached && resumed) {
                reloadDueAtMs = null
                reload()
                scheduleEventReload()
            }
        }

        applyPlaybackExtras(extras)

        audioManager =
            activity.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioMaxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume = -1f

        val container = activity.findViewById<ViewGroup>(R.id.overlay)
        val view =
            LayoutInflater.from(activity).inflate(R.layout.video_player_overlay, container, false)
        container.addView(view)
        rootView = view
        overlayRoot = view.findViewById(R.id.overlay_root)
        composeOverlay = view.findViewById(R.id.compose_overlay)
        composeZapList = view.findViewById(R.id.compose_zap_list)
        composeBouquetBar = view.findViewById(R.id.compose_bouquet_bar)
        composeBouquetBar?.bindOverlayBouquetBar(
            state = overlayUiState,
            onBouquetClick = { bouquet -> selectBouquet(bouquet) },
            onUserInteraction = { autohide() },
            onScrollInProgress = { scrolling ->
                if (scrolling) {
                    handler.removeCallbacks(autoHideRunnable)
                } else {
                    autohide()
                }
            }
        )
        if (tvOverlay) {
            composeOverlay!!.bindTvPlayerInfobar(
                state = overlayUiState,
                onPlay = { onPlay() },
                onRewind = { onRewind() },
                onForward = { onForward() },
                onInfo = { onInfo() },
                onList = { onList() },
                onAudio = { onSelectAudioTrack() },
                onSubtitle = { onSelectSubtitleTrack() },
                onSleepTimer = { onSleepTimer() },
                onUserInteraction = { autohide() }
            )
        } else {
            composeOverlay!!.bindVideoOverlayScreen(
                state = overlayUiState,
                onPlay = { onPlay() },
                onRewind = { onRewind() },
                onForward = { onForward() },
                onInfo = { onInfo() },
                onList = { onList() },
                onAudio = { onSelectAudioTrack() },
                onSubtitle = { onSelectSubtitleTrack() },
                onSleepTimer = { onSleepTimer() },
                onSeekChange = { progress -> seek(progress) }
            )
        }
        overlayUiState.onChoiceAction = { actionId, dialogTag ->
            onDialogAction(actionId, null, dialogTag)
        }
        if (tvOverlay) {
            bindTvChannelListIfAllowed()
        } else {
            bindPhoneZapList()
        }
        wireServiceListAndGestures()
        onServiceInfoChanged(true)
        backCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isOverlaysVisible()) {
                        hideOverlays()
                    } else {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback!!)
        if (tvOverlay) {
            tvSessionJob =
                activity.lifecycleScope.launch {
                    activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        sessions.status.collect { status ->
                            setTvStreamingChromeEnabled(status.allowsStreaming())
                        }
                    }
                }
        }
        attached = true
        renderedEventKey = eventKey(session)
        renderZapState()
        playbackJob = activity.lifecycleScope.launch {
            playback.uiState.collect { onSessionChanged(it) }
        }
        playerStateJob = activity.lifecycleScope.launch {
            player.state
                .map { (it.audioTracks > 0) to (it.subtitleTracks > 0) }
                .distinctUntilChanged()
                .collect { (audio, subtitles) ->
                    overlayUiState.showAudioButton = audio
                    overlayUiState.showSubtitleButton = subtitles
                }
        }
        autohide()
    }

    fun onResume() {
        if (!attached) {
            return
        }
        resumed = true
        showOverlays()
        reload()
        rearmEventReload()
    }

    fun onPause() {
        if (!attached) {
            return
        }
        resumed = false
        handler.removeCallbacks(autoHideRunnable)
        handler.removeCallbacks(issueReloadRunnable)
        playback.cancelReload()
        restoreBrightness()
    }

    fun detach() {
        if (!attached) {
            return
        }
        if (resumed) {
            onPause()
        }
        backCallback?.remove()
        backCallback = null
        tvSessionJob?.cancel()
        tvSessionJob = null
        playbackJob?.cancel()
        playbackJob = null
        playerStateJob?.cancel()
        playerStateJob = null
        playback.cancelReload()
        reloadDueAtMs = null
        tvChannelListBound = false
        phoneZapListBound = false
        activity.findViewById<View>(R.id.overlay)?.setOnTouchListener(null)
        val container = activity.findViewById<ViewGroup>(R.id.overlay)
        rootView?.let { child ->
            container.removeView(child)
        }
        rootView = null
        overlayRoot = null
        composeOverlay = null
        composeZapList = null
        composeBouquetBar = null
        gestureDetector = null
        attached = false
    }

    /** TV overlay only: hide zap / stream-another chrome unless session is Online. */
    fun setTvStreamingChromeEnabled(enabled: Boolean) {
        if (!tvOverlay) {
            return
        }
        if (enabled) {
            overlayUiState.zapServices = session.services
            overlayUiState.zapCurrentRef = session.serviceRef
            bindTvChannelListIfAllowed()
            refreshZapChrome()
        } else {
            unbindTvChannelList()
        }
    }

    private fun allowsTvStreaming(): Boolean {
        if (!tvOverlay) {
            return true
        }
        return sessions.status.value.allowsStreaming()
    }

    private fun bindTvChannelListIfAllowed() {
        val zapList = composeZapList ?: return
        if (!allowsTvStreaming()) {
            unbindTvChannelList()
            return
        }
        if (tvChannelListBound) {
            return
        }
        zapList.bindTvChannelList(
            state = overlayUiState,
            onServiceClick = { row -> onTvChannelPicked(row) },
            onSelectBouquet = { bouquet -> selectBouquet(bouquet) },
            onUserInteraction = { autohide() },
            onScrollInProgress = { scrolling ->
                if (scrolling) {
                    handler.removeCallbacks(autoHideRunnable)
                } else {
                    autohide()
                }
            }
        )
        tvChannelListBound = true
    }

    private fun unbindTvChannelList() {
        val zapList = composeZapList ?: return
        hideZapOverlays()
        overlayUiState.showListButton = false
        overlayUiState.zapServices = emptyList()
        zapList.setContent { }
        zapList.visibility = View.GONE
        zapList.isFocusable = false
        tvChannelListBound = false
    }

    private fun bindPhoneZapList() {
        val zapList = composeZapList ?: return
        if (phoneZapListBound) {
            return
        }
        zapList.bindPhoneZapList(
            state = overlayUiState,
            onServiceClick = { row -> zapToService(row) },
            onScrollInProgress = { scrolling ->
                if (scrolling) {
                    handler.removeCallbacks(autoHideRunnable)
                } else {
                    autohide()
                }
            }
        )
        phoneZapListBound = true
    }

    private fun wireServiceListAndGestures() {
        if (session.services.isEmpty()) {
            overlayUiState.showListButton = false
        }
        val zapList = composeZapList
        if (zapList != null) {
            servicesViewVisible = zapList.visibility == View.VISIBLE
        }

        gestureDetector =
            GestureDetector(
                activity,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        if (e1 == null) return true
                        val isGesturesEnabled =
                            PreferenceManager.getDefaultSharedPreferences(activity)
                                .getBoolean(DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES, true)
                        if (!isGesturesEnabled) return true

                        Log.d(
                            LOG_TAG,
                            String.format(
                                "distanceY=%s, DeltaY=%s",
                                distanceY,
                                e1.y - e2.y
                            )
                        )
                        val metrics = activity.resources.displayMetrics
                        val isRight = e1.rawX > (4 * metrics.widthPixels / 7)
                        val isLeft = e1.rawX < (3 * metrics.widthPixels / 7)

                        if (abs(distanceY) > abs(distanceX) && distanceY != 0f) {
                            if (isRight) {
                                onVolumeTouch(distanceY)
                            } else if (isLeft) {
                                onBrightnessTouch(distanceY)
                            }
                        } else if (abs(distanceX) > abs(distanceY) && distanceX != 0f) {
                            onChannelSwipe(distanceX, distanceY)
                        }
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        toggleViews()
                        return true
                    }
                }
            )

        activity.findViewById<View>(R.id.overlay).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                channelSwipe.reset()
            }
            val metrics = activity.resources.displayMetrics
            if (surfaceHeight == 0) {
                surfaceHeight = min(metrics.widthPixels, metrics.heightPixels)
            }
            if (surfaceWidth == 0) {
                surfaceWidth = max(metrics.widthPixels, metrics.heightPixels)
            }
            gestureDetector!!.onTouchEvent(event)
            true
        }
    }

    private fun onChannelSwipe(distanceX: Float, distanceY: Float) {
        val width = if (surfaceWidth > 0) {
            surfaceWidth
        } else {
            activity.resources.displayMetrics.widthPixels
        }
        when (channelSwipe.onScroll(distanceX, distanceY, width * 0.22f)) {
            VideoSwipe.Next -> if (movie == null) next() else onForward()
            VideoSwipe.Previous -> if (movie == null) previous() else onRewind()
            VideoSwipe.None -> Unit
        }
    }

    private fun onRewind() {
        player.setPosition(max(0.0f, player.state.value.position - seekStepSize))
        autohide()
    }

    private fun onForward() {
        player.setPosition(max(0.0f, player.state.value.position + seekStepSize))
        autohide()
    }

    private fun onPlay() {
        player.play()
        autohide()
    }

    private fun onSelectAudioTrack() {
        showTrackSelection(
            activity.getString(R.string.audio_tracks),
            player.audioTracks(),
            DIALOG_TAG_AUDIO_TRACK
        )
    }

    private fun onSelectSubtitleTrack() {
        showTrackSelection(
            activity.getString(R.string.subtitles),
            player.subtitleTracks(),
            DIALOG_TAG_SUBTITLE_TRACK
        )
    }

    /** Offers the durations, and Off while a timer runs (id 0). */
    private fun onSleepTimer() {
        val ids = if (session.sleepTimer != SleepTimer.Off) {
            listOf(0) + SLEEP_TIMER_MINUTES
        } else {
            SLEEP_TIMER_MINUTES
        }
        val labels = ids.map { minutes ->
            if (minutes == 0) {
                activity.getString(R.string.sleep_timer_off)
            } else {
                activity.getString(R.string.sleep_timer_minutes, minutes)
            }
        }
        overlayUiState.showChoice(
            activity.getString(R.string.sleeptimer),
            labels,
            ids.toIntArray(),
            DIALOG_TAG_SLEEP_TIMER
        )
    }

    private fun onInfo() {
        if (movie == null && currentService == null) return

        if (movie != null) {
            overlayUiState.showMovieDetail(movie!!)
            return
        }

        var event = currentService!!.now
        if (event == null) {
            event =
                Event(
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    currentService!!.serviceReference,
                    currentService!!.serviceName,
                    "",
                    "",
                    ""
                )
        }
        overlayUiState.showEpgDetail(activity, event)
    }

    private fun onList() {
        if (!allowsTvStreaming()) {
            return
        }
        if (tvOverlay) {
            showTvChannelList()
            return
        }
        if (!servicesViewVisible) {
            servicesViewVisible = true
            showZapOverlays()
        } else {
            hideZapOverlays()
            servicesViewVisible = false
        }
    }

    private fun showTrackSelection(
        title: String,
        descriptions: List<MediaTrack>?,
        dialogTag: String
    ) {
        if (descriptions == null || descriptions.isEmpty()) {
            playback.showMessage(UiText.Resource(R.string.no_tracks))
            return
        }
        val labels = descriptions.map { it.name }
        val ids = IntArray(descriptions.size) { idx -> descriptions[idx].id }
        overlayUiState.showChoice(title, labels, ids, dialogTag)
    }

    private fun onVolumeTouch(distanceY: Float) {
        val delta = (distanceY / surfaceHeight) * 100
        var currentVolume =
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / audioMaxVol.toFloat() * 100
        if (volume > 0) {
            currentVolume = volume
        }

        currentVolume += delta
        currentVolume = max(min(currentVolume, 100f), 0f)
        volume = currentVolume
        setVolume((currentVolume / 100 * audioMaxVol).toInt())
    }

    private fun setVolume(volume: Int) {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume != currentVol) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volume,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun onBrightnessTouch(distanceY: Float) {
        val delta = distanceY / surfaceHeight
        val window = activity.window
        val layoutParams = window.attributes
        if (savedScreenBrightness == null) {
            savedScreenBrightness = layoutParams.screenBrightness
        }
        layoutParams.screenBrightness = min(max(layoutParams.screenBrightness + delta, 0.01f), 1f)
        window.attributes = layoutParams
    }

    private fun restoreBrightness() {
        val brightness = savedScreenBrightness ?: return
        savedScreenBrightness = null
        val window = activity.window
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    /** Paints load results the view model publishes after the fact. */
    private fun onSessionChanged(session: VideoPlaybackUiState) {
        if (!attached) return
        if (session.sleepTimer == SleepTimer.Expired) {
            // Closing the player stops the stream and lets the screen lock again.
            activity.finish()
            return
        }
        overlayUiState.sleepTimer = session.sleepTimer
        val stream = session.stream
        if (stream != null) {
            playback.onStreamStarted()
            playZappedService(stream)
        }
        renderZapState()
        val key = eventKey(session)
        if (key != renderedEventKey) {
            renderedEventKey = key
            onServiceInfoChanged(false)
        }
    }

    private fun eventKey(session: VideoPlaybackUiState): String? =
        session.currentService?.let { "${it.serviceReference}#${it.now?.eventId}" }

    private fun renderZapState() {
        overlayUiState.zapServices = session.services
        overlayUiState.zapCurrentRef = session.serviceRef
        overlayUiState.bouquets = session.bouquets
        overlayUiState.selectedBouquetRef = session.bouquetRef
        refreshZapChrome()
    }

    private fun liveBouquetBarAvailable(): Boolean =
        movie == null && overlayUiState.bouquets.isNotEmpty()

    private fun refreshZapChrome() {
        if (!allowsTvStreaming()) {
            overlayUiState.showListButton = false
            hideZapOverlays()
            return
        }
        val showButton = session.services.isNotEmpty() || liveBouquetBarAvailable()
        overlayUiState.showListButton = showButton
        if (!showButton) {
            hideZapOverlays()
            return
        }
        if (isOverlaysVisible() && servicesViewVisible) {
            showZapOverlays()
        }
    }

    private fun selectBouquet(bouquet: BouquetService) {
        if (!allowsTvStreaming() || movie != null) {
            return
        }
        if (playback.selectBouquet(bouquet.reference)) {
            servicesViewVisible = true
            reload()
        }
        autohide()
    }

    private fun zap() {
        if (!allowsTvStreaming()) return
        val ref = session.serviceRef ?: return
        if (Service.isMarker(ref)) return
        playback.streamCurrent()
    }

    private fun playZappedService(stream: LiveStream.Ready) {
        if (!allowsTvStreaming()) return
        val session = session
        if (Service.isMarker(stream.reference)) return
        val streamingIntent =
            IntentFactory.getStreamServiceIntent(
                activity,
                stream,
                session.title ?: "",
                session.bouquetRef,
                session.currentService
            )
        activity.handleIntent(streamingIntent)

        onServiceInfoChanged(true)
    }

    fun applyPlaybackExtras(extras: Bundle?) {
        if (extras == null) return
        val incoming =
            VideoPlayback.overlayExtrasForActionView(
                extras.getString(TITLE),
                extras.getString(SERVICE_REFERENCE),
                extras.getString(BOUQUET_REFERENCE)
            )
        val changed = playback.applyExtras(
            incoming.title,
            incoming.serviceRef,
            incoming.bouquetRef,
            extras.getSerializableCompat<Serializable>(SERVICE_INFO)
        )
        if (rootView == null) return
        renderZapState()
        if (changed) {
            onServiceInfoChanged(true)
            if (resumed) {
                reload()
            }
        }
    }

    private fun previous() {
        if (!allowsTvStreaming()) return
        if (playback.step(forward = false)) zap()
    }

    private fun next() {
        if (!allowsTvStreaming()) return
        if (playback.step(forward = true)) zap()
    }

    private fun onServiceInfoChanged(doShowOverlay: Boolean) {
        Log.d(LOG_TAG, "service info changed!")
        if (doShowOverlay) {
            showOverlays()
        } else {
            updateViews()
        }
        scheduleEventReload()
    }

    /**
     * Reloads now/next when the running event ends, and again until the receiver has moved
     * on: right after the end its EPG may still report the old event.
     */
    private fun scheduleEventReload() {
        handler.removeCallbacks(issueReloadRunnable)
        if (currentService == null && movie == null) {
            reloadDueAtMs = null
            return
        }
        val now = System.currentTimeMillis()
        val schedule =
            eventReloadSchedule(currentService?.now, eventKey(session), reloadSchedule, now)
        reloadSchedule = schedule
        reloadDueAtMs = now + schedule.delayMs
        handler.postDelayed(issueReloadRunnable, schedule.delayMs)
    }

    /**
     * Re-arms a reload the pause dropped. Before its due time the remaining delay is kept,
     * without spending a retry; after it, the resume reload is that retry and the next one
     * is scheduled.
     */
    private fun rearmEventReload() {
        val due = reloadDueAtMs ?: return
        handler.removeCallbacks(issueReloadRunnable)
        val remaining = due - System.currentTimeMillis()
        if (remaining > 0) {
            handler.postDelayed(issueReloadRunnable, remaining)
        } else {
            scheduleEventReload()
        }
    }

    fun reload() {
        if (!attached || rootView == null) return
        playback.reload()
    }

    private fun seek(pos: Int) {
        val fpos = pos.toFloat()
        var length = player.state.value.lengthMs
        length = if (length > 0) length / 1000 else FAKE_LENGTH.toLong()
        player.setPosition(fpos / length)
    }

    /** Live TV is the session's call; engines report some live streams as seekable. */
    private fun isLive(): Boolean = movie == null && currentService != null

    private fun isRecording(): Boolean {
        val isDreamboxRecording = movie != null
        return isDreamboxRecording || (!isLive() && player.state.value.seekable)
    }

    private fun updateViews() {
        if (rootView == null) return

        val title = session.title
        overlayUiState.title = title ?: ""
        overlayUiState.showPvrControls = !isLive() && player.state.value.seekable

        if (movie != null || currentService != null) {
            overlayUiState.showInfoButton = true
            if (isRecording()) {
                val movieTitle = movie?.title
                overlayUiState.title =
                    if (!movieTitle.isNullOrEmpty()) movieTitle else (title ?: "")
            } else if (currentService != null) {
                val serviceName = currentService!!.serviceName
                overlayUiState.title =
                    if (serviceName.isNotEmpty()) serviceName else (title ?: "")
                val now = currentService!!.now
                overlayUiState.nowStart = now?.startTimeReadable ?: ""
                overlayUiState.nowTitle = now?.title ?: ""
                overlayUiState.nowDuration = now?.durationReadable ?: ""
                overlayUiState.nowEvent = now
                overlayUiState.showNow = true
            }

            val nextEvent = currentService?.next
            if (nextEvent != null && nextEvent.title.isNotEmpty()) {
                overlayUiState.nextStart = nextEvent.startTimeReadable
                overlayUiState.nextTitle = nextEvent.title
                overlayUiState.nextDuration = nextEvent.durationReadable
                overlayUiState.hasNext = true
            } else {
                overlayUiState.hasNext = false
            }
        } else {
            overlayUiState.showNow = false
            overlayUiState.hasNext = false
            overlayUiState.showInfoButton = false
        }
        if (currentService == null || isRecording()) {
            overlayUiState.nowEvent = null
        }
        updateProgress()
        overlayUiState.zapCurrentRef = session.serviceRef
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateProgress() {
        if (rootView == null) return
        val playerState = player.state.value
        val isSeekable = !isLive() && playerState.seekable
        overlayUiState.seekable = isSeekable
        var len = -1L
        var cur = -1L
        if (movie != null || currentService != null) {
            if (isRecording()) {
                var duration = playerState.lengthMs / 1000
                if (duration <= 0) {
                    val textLen =
                        if (movie != null && !movie!!.length.isNullOrEmpty()) {
                            movie!!.length
                        } else {
                            "00:00"
                        }
                    val l = textLen.split(":")
                    try {
                        duration = (l[0].toLong() * 60) + l[1].toLong()
                    } catch (nex: NumberFormatException) {
                        Log.w(LOG_TAG, "parse failed", nex)
                    } catch (iobex: IndexOutOfBoundsException) {
                        Log.w(LOG_TAG, "parse failed", iobex)
                    }
                }
                if (duration > 0) {
                    val pos = (duration * playerState.position).toLong()
                    overlayUiState.nowStart = DateTime.minutesAndSeconds(pos.toInt())
                    overlayUiState.nowTitle = movie?.serviceName ?: ""
                    overlayUiState.nowDuration = DateTime.minutesAndSeconds(duration.toInt())
                    overlayUiState.showNow = true
                } else {
                    overlayUiState.showNow = false
                }
                overlayUiState.hasNext = false
            } else {
                // The player's time on a live stream is the stream's, not the programme's.
                eventProgress(currentService?.now, System.currentTimeMillis())?.let {
                    len = it.totalSec
                    cur = it.elapsedSec
                }
            }
        }
        if (len <= 0 && !isLive()) {
            len = playerState.lengthMs / 1000
            cur = playerState.timeMs / 1000
        }

        if (len <= 0 && isSeekable) {
            len = FAKE_LENGTH.toLong()
            cur = (len * playerState.position).toLong()
        }

        if (len > 0 && cur >= 0) {
            overlayUiState.progressEnabled = true
            overlayUiState.progressMax = len.toInt()
            overlayUiState.progress = cur.toInt()
        } else {
            overlayUiState.progressEnabled = false
            overlayUiState.progressMax = 0
            overlayUiState.progress = 0
        }
    }

    fun autohide() {
        handler.removeCallbacks(autoHideRunnable)
        handler.postDelayed(autoHideRunnable, AUTOHIDE_DEFAULT_TIMEOUT.toLong())
    }

    fun onPictureInPictureModeChanged() {
        hideOverlays()
    }

    fun showOverlays() {
        if (activity.isInPictureInPictureMode) {
            hideOverlays()
            return
        }
        if (rootView == null) return
        handler.removeCallbacks(autoHideRunnable)
        updateViews()
        if (tvOverlay) {
            // TV shows either the channel list or the infobar, never both.
            composeOverlay?.visibility = if (servicesViewVisible) View.GONE else View.VISIBLE
        }
        if (servicesViewVisible) {
            showZapOverlays()
        } else if (tvOverlay) {
            hideZapOverlays()
        }
        fadeInView(overlayRoot)
        autohide()
        if (tvOverlay) {
            val target = if (servicesViewVisible) composeZapList else composeOverlay
            target?.requestFocus()
        }
    }

    fun hideOverlays() {
        if (rootView == null) return
        handler.removeCallbacks(autoHideRunnable)
        hideZapOverlays()
        fadeOutView(overlayRoot)
        if (tvOverlay) {
            servicesViewVisible = false
        }
    }

    private fun showTvChannelList() {
        if (!allowsTvStreaming() || movie != null) {
            return
        }
        servicesViewVisible = session.services.isNotEmpty() || liveBouquetBarAvailable()
        showOverlays()
    }

    /** OK in the TV channel list: zap and close; on the playing channel, only close. */
    private fun onTvChannelPicked(row: ServiceNowNext) {
        if (row.serviceReference == session.serviceRef) {
            hideOverlays()
            return
        }
        servicesViewVisible = false
        zapToService(row)
        showOverlays()
    }

    private fun showZapOverlays() {
        if (!allowsTvStreaming()) {
            hideZapOverlays()
            return
        }
        if (rootView == null) return
        val showChannels = session.services.isNotEmpty()
        val showBouquets = liveBouquetBarAvailable()
        if (!showChannels && !showBouquets) {
            hideZapOverlays()
            return
        }
        overlayUiState.zapCurrentRef = session.serviceRef
        overlayUiState.selectedBouquetRef = session.bouquetRef
        if (showChannels) {
            val composeZapList = this.composeZapList
            fadeInView(composeZapList)
        } else {
            fadeOutView(composeZapList)
        }
        if (showBouquets) {
            fadeInView(composeBouquetBar)
        } else {
            fadeOutView(composeBouquetBar)
        }
        autohide()
    }

    private fun hideZapOverlays() {
        if (rootView == null) return
        fadeOutView(composeZapList)
        fadeOutView(composeBouquetBar)
    }

    private fun fadeInView(v: View?) {
        if (v == null || v.visibility == View.VISIBLE) return
        v.visibility = View.VISIBLE
        v.alpha = 0.0f
        val alpha = if (tvOverlay) 1f else overlayAlpha
        v.animate().alpha(alpha).setListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.alpha = alpha
                }
            }
        )
    }

    private fun fadeOutView(v: View?) {
        if (v == null || v.visibility == View.GONE) return
        v.animate().alpha(0.0f).setListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.visibility = View.GONE
                }
            }
        )
    }

    fun toggleViews() {
        if (isOverlaysVisible()) {
            hideOverlays()
        } else {
            showOverlays()
        }
    }

    private fun isOverlaysVisible(): Boolean {
        val root = rootView ?: return false
        val sdroot = root.findViewById<View>(R.id.overlay_root)
        return sdroot.visibility == View.VISIBLE
    }

    fun onEvent(event: PlayerEvent) {
        if (rootView == null) return
        val spinner = activity.findViewById<View>(R.id.video_load_progress)
        when (event) {
            PlayerEvent.Opening -> fadeInView(spinner)

            // The spinner stays until a picture shows: audio often plays first.
            // TV leaves the infobar of a fresh zap to the auto-hide timer.
            PlayerEvent.Playing -> {
                updateProgress()
                if (!tvOverlay) {
                    hideOverlays()
                }
            }

            PlayerEvent.PictureReady -> fadeOutView(spinner)

            PlayerEvent.Progress -> updateProgress()

            is PlayerEvent.Failed -> {
                fadeOutView(spinner)
                playback.showMessage(UiText.Resource(R.string.playback_failed))
            }

            PlayerEvent.FellBackToVlc ->
                playback.showMessage(UiText.Resource(R.string.player_fell_back_to_vlc))

            PlayerEvent.SoftwareDecoding ->
                playback.showMessage(UiText.Resource(R.string.player_software_decoding))

            else -> Unit
        }
    }

    private fun zapToService(row: ServiceNowNext) {
        if (!allowsTvStreaming()) return
        val serviceRef = row.serviceReference
        if (Service.isMarker(serviceRef)) return
        playback.zapTo(row)
        zap()
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        when (dialogTag) {
            DIALOG_TAG_AUDIO_TRACK -> player.setAudioTrack(action)
            DIALOG_TAG_SUBTITLE_TRACK -> player.setSubtitleTrack(action)
            DIALOG_TAG_SLEEP_TIMER -> playback.setSleepTimer(action)
        }
    }

    fun onKeyDown(keyCode: Int): Boolean {
        var ret = false
        autohide()
        if (tvOverlay && onTvPlayerKey(keyCode)) {
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> {
                if (isOverlaysVisible()) {
                    hideOverlays()
                    ret = true
                } else {
                    return false
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isOverlaysVisible()) return false
                if (isRecording()) {
                    player.slower()
                    return true
                } else {
                    previous()
                }
                ret = true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isOverlaysVisible()) return false
                if (isRecording()) {
                    onForward()
                } else {
                    next()
                }
                ret = true
            }

            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (isRecording()) {
                    onRewind()
                    ret = true
                }
            }

            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (isRecording()) {
                    onForward()
                    ret = true
                }
            }

            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player.play()
                ret = true
            }
        }
        if (!isOverlaysVisible()) {
            showOverlays()
            ret = true
        }
        return ret
    }

    private fun onTvPlayerKey(keyCode: Int): Boolean {
        val key = tvPlayerKey(keyCode, isOverlaysVisible(), live = !isRecording())
        when (key) {
            TvPlayerKey.NextChannel -> next()
            TvPlayerKey.PreviousChannel -> previous()
            TvPlayerKey.ChannelList -> showTvChannelList()
            TvPlayerKey.None -> return false
        }
        if (key != TvPlayerKey.ChannelList) {
            servicesViewVisible = false
            showOverlays()
        }
        return true
    }

    companion object {
        const val TITLE: String = "title"
        const val SERVICE_INFO: String = "serviceInfo"
        const val BOUQUET_REFERENCE: String = "bouquetRef"
        const val SERVICE_REFERENCE: String = "serviceRef"

        const val DIALOG_TAG_AUDIO_TRACK: String = "dialog_audio_track"
        const val DIALOG_TAG_SUBTITLE_TRACK: String = "dialog_subtitle_track"
        const val DIALOG_TAG_SLEEP_TIMER: String = "dialog_sleep_timer"

        private const val AUTOHIDE_DEFAULT_TIMEOUT: Int = 7000
        private const val FAKE_LENGTH: Int = 10000

        private val LOG_TAG: String = VideoOverlayController::class.java.simpleName

        /** Phone chrome stays see-through so the video shows; TV panels carry their own. */
        var overlayAlpha: Float = 0.85f

        var seekStepSize: Float = 0.02f
    }
}
