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
import android.util.DisplayMetrics
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
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie as EnigmaMovie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.loadEpgNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.ui.allowsStreaming
import net.reichholf.dreamdroid.tv.ui.bindTvZapList
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.video.VLCPlayer
import net.reichholf.dreamdroid.video.VideoPlayback
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration
import org.videolan.libvlc.MediaPlayer

/**
 * VLC overlay chrome hosted by [VideoActivity]. Not a Fragment — inflate into
 * [R.id.overlay], wire Compose / gestures, and drive playback chrome.
 */
class VideoOverlayController(private val activity: VideoActivity) :
    MediaPlayer.EventListener,
    ItemClickSupport.OnItemClickListener,
    DialogActionListener {

    private var attached: Boolean = false
    private var resumed: Boolean = false
    private var rootView: View? = null
    private val playbackArgs: Bundle = Bundle()

    private var surfaceHeight: Int = 0
    private var surfaceWidth: Int = 0

    private var title: String? = null
    private var serviceRef: String? = null
    private var bouquetRef: String? = null

    private lateinit var serviceList: ArrayList<ServiceNowNext>
    private var currentService: ServiceNowNext? = null
    private var movie: EnigmaMovie? = null

    private lateinit var handler: Handler
    private lateinit var autoHideRunnable: Runnable
    private lateinit var issueReloadRunnable: Runnable

    private var itemClickSupport: ItemClickSupport? = null

    private var overlayRoot: View? = null
    private var servicesView: RecyclerView? = null
    private var composeOverlay: ComposeView? = null
    private var composeZapList: ComposeView? = null

    private val overlayUiState: VideoOverlayUiState = VideoOverlayUiState()

    private var gestureDetector: GestureDetectorCompat? = null
    private lateinit var audioManager: AudioManager
    private var audioMaxVol: Int = 0
    private var volume: Float = 0f
    private var servicesViewVisible: Boolean = false

    private var loadJob: Job? = null
    private var tvSessionJob: Job? = null
    private var tvZapListBound: Boolean = false
    private var savedScreenBrightness: Float? = null
    private var backCallback: OnBackPressedCallback? = null

    fun attach(extras: Bundle?) {
        if (attached) {
            applyPlaybackExtras(extras)
            return
        }
        serviceList = ArrayList()
        handler = Handler(Looper.getMainLooper())
        servicesViewVisible = false
        autoHideRunnable = Runnable { hideOverlays() }
        issueReloadRunnable = Runnable { reload() }

        playbackArgs.clear()
        if (extras != null) {
            playbackArgs.putAll(extras)
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
        servicesView = view.findViewById(R.id.servicelist)
        composeOverlay = view.findViewById(R.id.compose_overlay)
        composeZapList = view.findViewById(R.id.compose_zap_list)
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
            uncappedDetailSheets = composeZapList != null
        )
        overlayUiState.onChoiceAction = { actionId, dialogTag ->
            onDialogAction(actionId, null, dialogTag)
        }
        bindTvZapListIfAllowed()
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
        if (composeZapList != null) {
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
        cancelLoad()
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
        cancelLoad()
        tvZapListBound = false
        itemClickSupport = null
        activity.findViewById<View>(R.id.overlay)?.setOnTouchListener(null)
        val container = activity.findViewById<ViewGroup>(R.id.overlay)
        rootView?.let { child ->
            container.removeView(child)
        }
        rootView = null
        overlayRoot = null
        servicesView = null
        composeOverlay = null
        composeZapList = null
        gestureDetector = null
        attached = false
    }

    /** TV overlay only: hide zap / stream-another chrome unless session is Online. */
    fun setTvStreamingChromeEnabled(enabled: Boolean) {
        if (composeZapList == null) {
            return
        }
        if (enabled) {
            overlayUiState.zapServices = serviceList.toList()
            overlayUiState.zapCurrentRef = serviceRef
            bindTvZapListIfAllowed()
            if (serviceList.isNotEmpty()) {
                overlayUiState.showListButton = true
            }
        } else {
            unbindTvZapList()
        }
    }

    private fun allowsTvStreaming(): Boolean {
        if (composeZapList == null) {
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

    private fun wireServiceListAndGestures() {
        val servicesView = this.servicesView
        if (serviceList.isEmpty()) {
            overlayUiState.showListButton = false
        }
        if (servicesView != null) {
            servicesView.layoutManager = GridLayoutManager(activity, 1)
            servicesView.addItemDecoration(
                SpacesItemDecoration(
                    activity.resources.getDimensionPixelSize(R.dimen.recylcerview_content_margin)
                )
            )
            itemClickSupport = ItemClickSupport.addTo(servicesView)
            itemClickSupport!!.setOnItemClickListener(this)

            servicesView.adapter = ServiceAdapter(activity, serviceList)
            servicesView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            autohide()
                        } else {
                            handler.removeCallbacks(autoHideRunnable)
                        }
                    }
                }
            )
            servicesViewVisible = servicesView.visibility == View.VISIBLE
        }
        val zapList = composeZapList
        if (zapList != null) {
            servicesViewVisible = zapList.visibility == View.VISIBLE
        }

        gestureDetector =
            GestureDetectorCompat(
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
                        val metrics = DisplayMetrics()
                        @Suppress("DEPRECATION")
                        activity.windowManager.defaultDisplay.getMetrics(metrics)
                        val isRight = e1.rawX > (4 * metrics.widthPixels / 7)
                        val isLeft = e1.rawX < (3 * metrics.widthPixels / 7)

                        if (abs(distanceY) > abs(distanceX) && distanceY != 0f) {
                            if (isRight) {
                                onVolumeTouch(distanceY)
                            } else if (isLeft) {
                                onBrightnessTouch(distanceY)
                            }
                        } else if (abs(distanceX) > abs(distanceY) && distanceX != 0f) {
                            // TODO: prev/next gesture handling)
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
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(metrics)
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

    private fun applyServiceList(services: ArrayList<ServiceNowNext>) {
        serviceList.clear()
        servicesView?.adapter?.notifyDataSetChanged()
        serviceList.addAll(services)
        overlayUiState.zapServices = serviceList.toList()
        overlayUiState.zapCurrentRef = serviceRef
        for (service in serviceList) {
            if (service.serviceReference == serviceRef) {
                val oldService = currentService
                currentService = service
                movie = null
                val eventid = currentService!!.now?.eventId ?: "-1"
                val oldEventId = oldService?.now?.eventId ?: "-2"
                if (oldService == null || eventid != oldEventId) {
                    onServiceInfoChanged(false)
                }
            }
            servicesView?.adapter?.notifyDataSetChanged()
        }
        if (serviceList.isEmpty() || !allowsTvStreaming()) {
            overlayUiState.showListButton = false
            hideZapOverlays()
        } else {
            overlayUiState.showListButton = true
            if (isOverlaysVisible() && servicesViewVisible) {
                showZapOverlays()
            }
        }
    }

    private fun zap() {
        if (!allowsTvStreaming()) return
        if (Service.isMarker(serviceRef)) return
        val serviceInfo = serviceInfoForIntent()
        val streamingIntent =
            IntentFactory.getStreamServiceIntent(
                activity,
                serviceRef!!,
                title ?: "",
                bouquetRef,
                serviceInfo as? ServiceNowNext
            )
        val zapExtras =
            VideoPlayback.overlayExtrasForZap(title, serviceRef, bouquetRef)
        playbackArgs.putString(TITLE, zapExtras.title)
        playbackArgs.putString(SERVICE_REFERENCE, zapExtras.serviceRef)
        playbackArgs.putString(BOUQUET_REFERENCE, zapExtras.bouquetRef)
        playbackArgs.putSerializable(SERVICE_INFO, serviceInfo)
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
        if (playbackArgs !== extras) {
            playbackArgs.putString(TITLE, incoming.title)
            playbackArgs.putString(SERVICE_REFERENCE, incoming.serviceRef)
            playbackArgs.putString(BOUQUET_REFERENCE, incoming.bouquetRef)
            if (extras.containsKey(SERVICE_INFO)) {
                @Suppress("DEPRECATION")
                val serviceInfo = extras.get(SERVICE_INFO) as java.io.Serializable?
                playbackArgs.putSerializable(SERVICE_INFO, serviceInfo)
            } else {
                playbackArgs.remove(SERVICE_INFO)
            }
        }

        if (!this::serviceList.isInitialized) return

        val refsChanged =
            incoming.serviceRef != serviceRef || incoming.bouquetRef != bouquetRef
        val titleChanged = incoming.title != title
        title = incoming.title
        serviceRef = incoming.serviceRef
        bouquetRef = incoming.bouquetRef

        @Suppress("DEPRECATION")
        when (val serviceInfo = extras.get(SERVICE_INFO)) {
            is EnigmaMovie -> {
                movie = serviceInfo
                currentService = null
            }

            is ServiceNowNext -> {
                currentService = serviceInfo
                movie = null
            }

            else -> if (refsChanged) {
                movie = null
                currentService = null
            }
        }

        if ((titleChanged || refsChanged) && rootView != null && this::handler.isInitialized) {
            onServiceInfoChanged(true)
            if (resumed) {
                reload()
            }
        }
    }

    private fun serviceInfoForIntent(): java.io.Serializable? {
        if (movie != null) {
            return movie
        }
        if (currentService != null) {
            return currentService
        }
        return null
    }

    private fun getPreviousServiceInfo(): ServiceNowNext? {
        val index = VideoPlayback.previousIndex(getCurrentServiceIndex(), serviceList.size)
        if (index < 0) return null
        return serviceList[index]
    }

    private fun previous() {
        if (!allowsTvStreaming()) return
        val serviceInfo = getPreviousServiceInfo() ?: return
        currentService = serviceInfo
        movie = null
        serviceRef = currentService!!.serviceReference
        title = currentService!!.serviceName
        zap()
    }

    private fun getNextServiceInfo(): ServiceNowNext? {
        val index = VideoPlayback.nextIndex(getCurrentServiceIndex(), serviceList.size)
        if (index < 0) return null
        return serviceList[index]
    }

    private fun next() {
        if (!allowsTvStreaming()) return
        val serviceInfo = getNextServiceInfo() ?: return
        currentService = serviceInfo
        movie = null
        serviceRef = currentService!!.serviceReference
        title = currentService!!.serviceName
        zap()
    }

    private fun getCurrentServiceIndex(): Int {
        if (serviceList.isEmpty()) return -1
        var idx = 0
        for (service in serviceList) {
            if (service.serviceReference == serviceRef) return idx
            idx++
        }
        return -1
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
        if (bouquetRef.isNullOrEmpty()) return
        if (!attached || rootView == null) return
        cancelLoad()
        val params = arrayListOf(NameValuePair("bRef", bouquetRef))
        loadJob =
            activity.lifecycleScope.launch {
                val result = loadEpgNowNext(activity, params)
                if (!attached) {
                    return@launch
                }
                onEpgNowNextReady(result.success, result.rows, result.errorText)
            }
    }

    private fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun onEpgNowNextReady(
        success: Boolean,
        rows: List<ServiceNowNext>,
        errorText: String?
    ) {
        if (!attached) return
        if (!success) {
            val message = errorText ?: activity.getString(R.string.get_content_error)
            Log.e(LOG_TAG, message)
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            return
        }
        applyServiceList(ArrayList(rows))
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
            val next = nextEvent?.title
            val hasNext = !next.isNullOrEmpty()
            if (hasNext) {
                overlayUiState.nextStart = nextEvent?.startTimeReadable ?: ""
                overlayUiState.nextTitle = nextEvent?.title ?: ""
                overlayUiState.nextDuration = nextEvent?.durationReadable ?: ""
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
        servicesView?.adapter?.notifyDataSetChanged()
        overlayUiState.zapCurrentRef = serviceRef
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
                        Log.w(LOG_TAG, nex.localizedMessage)
                    } catch (iobex: IndexOutOfBoundsException) {
                        Log.w(LOG_TAG, iobex.localizedMessage)
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
        if (serviceList.isEmpty() || !allowsTvStreaming()) {
            hideZapOverlays()
            return
        }
        if (rootView == null) return
        overlayUiState.zapCurrentRef = serviceRef
        val composeZapList = this.composeZapList
        if (composeZapList != null) {
            fadeInView(composeZapList)
        } else {
            val servicesView = this.servicesView
            if (servicesView != null) {
                servicesView.layoutManager?.scrollToPosition(getCurrentServiceIndex())
                fadeInView(servicesView)
            }
        }
        autohide()
    }

    private fun hideZapOverlays() {
        if (rootView == null) return
        fadeOutView(composeZapList)
        fadeOutView(servicesView)
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

    override fun onItemClick(recyclerView: RecyclerView, v: View, position: Int, id: Long) {
        zapToService(serviceList[position])
    }

    private fun zapToService(row: ServiceNowNext) {
        if (!allowsTvStreaming()) return
        val serviceRef = row.serviceReference
        if (Service.isMarker(serviceRef)) return
        currentService = row
        movie = null
        this.serviceRef = serviceRef
        title = row.serviceName
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
