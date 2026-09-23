package net.reichholf.dreamdroid.ui.video

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
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
import android.widget.Toast
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
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie as EnigmaMovie
import net.reichholf.dreamdroid.enigma.Service as BouquetService
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.getSerializableCompat
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.ui.allowsStreaming
import net.reichholf.dreamdroid.tv.ui.bindTvZapList
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.video.VLCPlayer
import net.reichholf.dreamdroid.video.VideoPlayback
import net.reichholf.dreamdroid.video.startLiveServiceStream
import org.videolan.libvlc.MediaPlayer

/**
 * VLC overlay chrome hosted by [VideoActivity]. Not a Fragment — inflate into
 * [R.id.overlay], wire Compose / gestures, and drive playback chrome.
 */
class VideoOverlayController(
    private val activity: VideoActivity,
    private val playback: VideoPlaybackViewModel
) : MediaPlayer.EventListener,
    DialogActionListener {

    private var attached: Boolean = false
    private var resumed: Boolean = false
    private var rootView: View? = null

    private var surfaceHeight: Int = 0
    private var surfaceWidth: Int = 0

    private val session: VideoPlaybackSession get() = playback.session.value
    private val movie: EnigmaMovie? get() = session.movie
    private val currentService: ServiceNowNext? get() = session.currentService

    private lateinit var handler: Handler
    private lateinit var autoHideRunnable: Runnable
    private lateinit var issueReloadRunnable: Runnable

    private val tvOverlay: Boolean = DreamDroid.isTV(activity)
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

    private var zapBeforeStreamJob: Job? = null
    private var playbackJob: Job? = null
    private var renderedEventKey: String? = null
    private var tvSessionJob: Job? = null
    private var tvZapListBound: Boolean = false
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
        autoHideRunnable = Runnable { hideOverlays() }
        issueReloadRunnable = Runnable { reload() }

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
        composeOverlay!!.bindVideoOverlayScreen(
            state = overlayUiState,
            onPlay = { onPlay() },
            onRewind = { onRewind() },
            onForward = { onForward() },
            onInfo = { onInfo() },
            onList = { onList() },
            onAudio = { onSelectAudioTrack() },
            onSubtitle = { onSelectSubtitleTrack() },
            onSeekChange = { progress -> seek(progress) },
            uncappedDetailSheets = tvOverlay
        )
        overlayUiState.onChoiceAction = { actionId, dialogTag ->
            onDialogAction(actionId, null, dialogTag)
        }
        if (tvOverlay) {
            bindTvZapListIfAllowed()
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
                        SessionConnectionHolder.shared.status.collect { status ->
                            setTvStreamingChromeEnabled(status.allowsStreaming())
                        }
                    }
                }
        }
        attached = true
        renderedEventKey = eventKey(session)
        renderZapState()
        playbackJob = activity.lifecycleScope.launch {
            launch { playback.session.collect { onSessionChanged(it) } }
            playback.errors.collect { message ->
                Log.e(LOG_TAG, message)
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
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
        zapBeforeStreamJob?.cancel()
        zapBeforeStreamJob = null
        playbackJob?.cancel()
        playbackJob = null
        playback.cancelReload()
        tvZapListBound = false
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
            bindTvZapListIfAllowed()
            refreshZapChrome()
        } else {
            unbindTvZapList()
        }
    }

    private fun allowsTvStreaming(): Boolean {
        if (!tvOverlay) {
            return true
        }
        return SessionConnectionHolder.shared.status.value.allowsStreaming()
    }

    private fun bindTvZapListIfAllowed() {
        val zapList = composeZapList ?: return
        if (!allowsTvStreaming()) {
            unbindTvZapList()
            return
        }
        if (tvZapListBound) {
            return
        }
        zapList.bindTvZapList(
            state = overlayUiState,
            onServiceClick = { row -> zapToService(row) },
            onUserInteraction = { autohide() },
            onScrollInProgress = { scrolling ->
                if (scrolling) {
                    handler.removeCallbacks(autoHideRunnable)
                } else {
                    autohide()
                }
            }
        )
        tvZapListBound = true
    }

    private fun unbindTvZapList() {
        val zapList = composeZapList ?: return
        hideZapOverlays()
        overlayUiState.showListButton = false
        overlayUiState.zapServices = emptyList()
        zapList.setContent { }
        zapList.visibility = View.GONE
        zapList.isFocusable = false
        tvZapListBound = false
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
        val p = VLCPlayer.get()!!
        p.setPosition(max(0.0f, p.getPosition() - seekStepSize))
        autohide()
    }

    private fun onForward() {
        val p = VLCPlayer.get()!!
        p.setPosition(max(0.0f, p.getPosition() + seekStepSize))
        autohide()
    }

    private fun onPlay() {
        VLCPlayer.get()!!.play()
        autohide()
    }

    fun onUpdateButtons() {
        val player = VLCPlayer.get() ?: return
        overlayUiState.showAudioButton = player.getAudioTracksCount() > 0
        overlayUiState.showSubtitleButton = player.getSubtitleTracksCount() > 0
    }

    private fun onSelectAudioTrack() {
        val player = VLCPlayer.getMediaPlayer()!!
        showTrackSelection(
            activity.getString(R.string.audio_tracks),
            player.audioTracks,
            DIALOG_TAG_AUDIO_TRACK
        )
    }

    private fun onSelectSubtitleTrack() {
        val player = VLCPlayer.getMediaPlayer()!!
        showTrackSelection(
            activity.getString(R.string.subtitles),
            player.spuTracks,
            DIALOG_TAG_SUBTITLE_TRACK
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
        descriptions: Array<MediaPlayer.TrackDescription>?,
        dialogTag: String
    ) {
        if (descriptions == null || descriptions.isEmpty()) {
            Toast.makeText(activity, R.string.no_tracks, Toast.LENGTH_SHORT).show()
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
    private fun onSessionChanged(session: VideoPlaybackSession) {
        if (!attached) return
        renderZapState()
        val key = eventKey(session)
        if (key != renderedEventKey) {
            renderedEventKey = key
            onServiceInfoChanged(false)
        }
    }

    private fun eventKey(session: VideoPlaybackSession): String? =
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
        zapBeforeStreamJob?.cancel()
        zapBeforeStreamJob = activity.startLiveServiceStream(activity, ref) {
            playZappedService()
        }
    }

    private fun playZappedService() {
        if (!allowsTvStreaming()) return
        val session = session
        val ref = session.serviceRef ?: return
        if (Service.isMarker(ref)) return
        val streamingIntent =
            IntentFactory.getStreamServiceIntent(
                activity,
                ref,
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
        if (currentService == null && movie == null) return
        handler.removeCallbacks(issueReloadRunnable)
        val now = currentService?.now
        val start = now?.start
        val duration = now?.duration
        if (
            duration != null &&
            start != null &&
            duration.isNotEmpty() &&
            start.isNotEmpty() &&
            duration != Python.NONE &&
            start != Python.NONE
        ) {
            val eventStart = (start.toDouble()).toLong() * 1000
            val eventEnd = eventStart + (duration.toDouble()).toLong() * 1000
            val nowMs = System.currentTimeMillis()
            var delay = eventEnd - nowMs
            if (eventEnd <= nowMs) {
                delay = nowMs // outdated, reload in few seconds
            }
            delay += 2000
            handler.postDelayed(issueReloadRunnable, delay)
        } else {
            Log.i(LOG_TAG, "No Eventinfo present, will update in 5 Minutes!")
            handler.postDelayed(issueReloadRunnable, 300000)
        }
    }

    fun reload() {
        if (!attached || rootView == null) return
        playback.reload()
    }

    private fun seek(pos: Int) {
        val player = VLCPlayer.get() ?: return
        val fpos = pos.toFloat()
        var length = player.getLength()
        length = if (length > 0) length / 1000 else FAKE_LENGTH.toLong()
        player.setPosition(fpos / length)
    }

    private fun isRecording(): Boolean {
        val isDreamboxRecording = movie != null
        return VLCPlayer.get()!!.isSeekable() || isDreamboxRecording
    }

    private fun updateViews() {
        if (rootView == null) return

        val title = session.title
        overlayUiState.title = title ?: ""
        val player = VLCPlayer.get()
        overlayUiState.showPvrControls = player != null && player.isSeekable()

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
        updateProgress()
        overlayUiState.zapCurrentRef = session.serviceRef
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateProgress() {
        if (rootView == null) return
        val player = VLCPlayer.get()
        val isSeekable = player != null && player.isSeekable()
        overlayUiState.seekable = isSeekable
        var len = -1L
        var cur = -1L
        if (movie != null || currentService != null) {
            if (isRecording()) {
                var duration = if (player != null) player.getLength() / 1000 else 0L
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
                if (duration > 0 && player != null) {
                    val pos = (duration * player.getPosition()).toLong()
                    overlayUiState.nowStart = DateTime.minutesAndSeconds(pos.toInt())
                    overlayUiState.nowTitle = movie?.serviceName ?: ""
                    overlayUiState.nowDuration = DateTime.minutesAndSeconds(duration.toInt())
                    overlayUiState.showNow = true
                } else {
                    overlayUiState.showNow = false
                }
                overlayUiState.hasNext = false
            } else if (currentService?.now != null) {
                val now = currentService!!.now!!
                val duration = now.duration
                val start = now.start

                if (
                    duration.isNotEmpty() &&
                    start.isNotEmpty() &&
                    duration != Python.NONE &&
                    start != Python.NONE
                ) {
                    try {
                        len = duration.toDouble().toLong()
                        cur = len - DateTime.getRemaining(duration, start) * 60L
                    } catch (e: Exception) {
                        Log.e(DreamDroid.LOG_TAG, e.toString())
                    }
                }
            }
        }
        if (player != null && len <= 0) {
            len = player.getLength() / 1000
            cur = player.getTime() / 1000
        }

        if (player != null && len <= 0 && isSeekable) {
            len = FAKE_LENGTH.toLong()
            cur = (len * player.getPosition()).toLong()
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

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        hideOverlays()
    }

    fun showOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) {
            hideOverlays()
            return
        }
        if (rootView == null) return
        handler.removeCallbacks(autoHideRunnable)
        updateViews()
        if (servicesViewVisible) {
            showZapOverlays()
        }
        fadeInView(overlayRoot)
        autohide()
    }

    fun hideOverlays() {
        if (rootView == null) return
        handler.removeCallbacks(autoHideRunnable)
        hideZapOverlays()
        fadeOutView(overlayRoot)
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
        v.animate().alpha(overlayAlpha).setListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.alpha = overlayAlpha
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

    override fun onEvent(event: MediaPlayer.Event) {
        val view = rootView ?: return
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                val progressView = view.findViewById<View>(R.id.video_load_progress)
                fadeInView(progressView)
            }

            MediaPlayer.Event.Playing -> {
                val progressView = view.findViewById<View>(R.id.video_load_progress)
                fadeOutView(progressView)
                updateProgress()
                hideOverlays()
            }

            MediaPlayer.Event.PositionChanged -> updateProgress()

            MediaPlayer.Event.EncounteredError ->
                Toast.makeText(activity, R.string.playback_failed, Toast.LENGTH_LONG).show()
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
        val player = VLCPlayer.getMediaPlayer()!!
        when (dialogTag) {
            DIALOG_TAG_AUDIO_TRACK -> player.setAudioTrack(action)
            DIALOG_TAG_SUBTITLE_TRACK -> player.setSpuTrack(action)
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var ret = false
        autohide()
        val player = VLCPlayer.get()!!
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

    companion object {
        const val TITLE: String = "title"
        const val SERVICE_INFO: String = "serviceInfo"
        const val BOUQUET_REFERENCE: String = "bouquetRef"
        const val SERVICE_REFERENCE: String = "serviceRef"

        const val DIALOG_TAG_AUDIO_TRACK: String = "dialog_audio_track"
        const val DIALOG_TAG_SUBTITLE_TRACK: String = "dialog_subtitle_track"

        private const val AUTOHIDE_DEFAULT_TIMEOUT: Int = 7000
        private const val FAKE_LENGTH: Int = 10000

        private val LOG_TAG: String = VideoOverlayController::class.java.simpleName

        /** Live TV chrome stays slightly see-through so the video is always visible. */
        var overlayAlpha: Float = 0.85f

        var seekStepSize: Float = 0.02f
    }
}
