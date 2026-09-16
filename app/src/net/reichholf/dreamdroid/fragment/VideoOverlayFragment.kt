package net.reichholf.dreamdroid.fragment

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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.leanback.widget.HorizontalGridView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie as EnigmaMovie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.launchEpgNowNextLoad
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.fragment.EpgDetailDialog
import net.reichholf.dreamdroid.tv.fragment.MovieDetailDialog
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import net.reichholf.dreamdroid.ui.video.bindVideoOverlayScreen
import net.reichholf.dreamdroid.ui.video.showEpgDetail
import net.reichholf.dreamdroid.ui.video.showMovieDetail
import net.reichholf.dreamdroid.video.VLCPlayer
import net.reichholf.dreamdroid.video.VideoPlayback
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration
import org.videolan.libvlc.MediaPlayer

/**
 * Kotlin port of the VLC overlay fragment. Public API matches the former Java class
 * so [VideoActivity] and existing instrumented tests keep working.
 */
class VideoOverlayFragment :
    Fragment(),
    MediaPlayer.EventListener,
    ItemClickSupport.OnItemClickListener,
    DialogActionListener {

    protected var surfaceHeight: Int = 0
    protected var surfaceWidth: Int = 0

    protected var title: String? = null
    protected var serviceRef: String? = null
    protected var bouquetRef: String? = null

    protected lateinit var serviceList: ArrayList<ServiceNowNext>
    protected var currentService: ServiceNowNext? = null
    protected var movie: EnigmaMovie? = null

    protected lateinit var handler: Handler
    protected lateinit var autoHideRunnable: Runnable
    protected lateinit var issueReloadRunnable: Runnable

    protected var itemClickSupport: ItemClickSupport? = null

    protected var overlayRoot: View? = null
    protected var servicesView: RecyclerView? = null
    protected var composeOverlay: ComposeView? = null

    protected val overlayUiState: VideoOverlayUiState = VideoOverlayUiState()

    private var gestureDetector: GestureDetectorCompat? = null
    private lateinit var audioManager: AudioManager
    private var audioMaxVol: Int = 0
    private var volume: Float = 0f
    private var servicesViewVisible: Boolean = false

    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        retainInstance = true
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
        serviceList = ArrayList()
        handler = Handler(Looper.getMainLooper())
        servicesViewVisible = false
        autoHideRunnable = Runnable { hideOverlays() }
        issueReloadRunnable = Runnable { reload() }
        applyPlaybackExtras(requireArguments())

        audioManager =
            requireActivity().applicationContext.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager
        audioMaxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        volume = -1f

        autohide()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.video_player_overlay, container, false)
        overlayRoot = view.findViewById(R.id.overlay_root)
        servicesView = view.findViewById(R.id.servicelist)
        composeOverlay = view.findViewById(R.id.compose_overlay)
        composeOverlay!!.bindVideoOverlayScreen(
            state = overlayUiState,
            onPlay = { onPlay() },
            onRewind = { onRewind() },
            onForward = { onForward() },
            onInfo = { onInfo() },
            onList = { onList() },
            onAudio = { onSelectAudioTrack() },
            onSubtitle = { onSelectSubtitleTrack() },
            onSeekChange = { progress -> seek(progress) }
        )
        overlayUiState.onChoiceAction = { actionId, dialogTag ->
            onDialogAction(actionId, null, dialogTag)
        }
        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val servicesView = this.servicesView
        if (servicesView != null) {
            if (DreamDroid.isTV(requireContext())) {
                val gridView = servicesView as HorizontalGridView
                gridView.setNumRows(1)
            } else {
                servicesView.layoutManager = GridLayoutManager(requireActivity(), 1)
            }
            if (serviceList.isEmpty()) {
                overlayUiState.showListButton = false
            }
            servicesView.addItemDecoration(
                SpacesItemDecoration(
                    requireActivity().resources.getDimensionPixelSize(
                        R.dimen.recylcerview_content_margin
                    )
                )
            )
            itemClickSupport = ItemClickSupport.addTo(servicesView)
            itemClickSupport!!.setOnItemClickListener(this)

            servicesView.adapter = ServiceAdapter(requireActivity(), serviceList)
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

        gestureDetector =
            GestureDetectorCompat(
                requireActivity(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        if (e1 == null) return true
                        val isGesturesEnabled =
                            PreferenceManager.getDefaultSharedPreferences(requireContext())
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
                        requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
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

        requireActivity().findViewById<View>(R.id.overlay).setOnTouchListener { _, event ->
            val metrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
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

    protected fun onRewind() {
        val p = VLCPlayer.get()!!
        p.setPosition(max(0.0f, p.getPosition() - seekStepSize))
        autohide()
    }

    protected fun onForward() {
        val p = VLCPlayer.get()!!
        p.setPosition(max(0.0f, p.getPosition() + seekStepSize))
        autohide()
    }

    protected fun onPlay() {
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
            getString(R.string.audio_tracks),
            player.audioTracks,
            DIALOG_TAG_AUDIO_TRACK
        )
    }

    private fun onSelectSubtitleTrack() {
        val player = VLCPlayer.getMediaPlayer()!!
        showTrackSelection(
            getString(R.string.subtitles),
            player.spuTracks,
            DIALOG_TAG_SUBTITLE_TRACK
        )
    }

    private fun onInfo() {
        if (movie == null && currentService == null) return

        if (movie != null) {
            if (DreamDroid.isTV(requireContext())) {
                MovieDetailDialog.newInstance(movie!!)
                    .show(parentFragmentManager, "details_dialog_tv")
            } else {
                overlayUiState.showMovieDetail(movie!!)
            }
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
        if (DreamDroid.isTV(requireContext())) {
            EpgDetailDialog.newInstance(event)
                .show(parentFragmentManager, "details_dialog_tv")
        } else {
            overlayUiState.showEpgDetail(requireContext(), event)
        }
    }

    private fun onList() {
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
        // this should actually never be true, but just to be sure we do it anyways
        if (descriptions == null || descriptions.isEmpty()) {
            Toast.makeText(context, R.string.no_tracks, Toast.LENGTH_SHORT).show()
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

    protected fun setVolume(volume: Int) {
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
        val window = requireActivity().window
        val layoutParams = window.attributes
        layoutParams.screenBrightness = min(max(layoutParams.screenBrightness + delta, 0.01f), 1f)
        window.attributes = layoutParams
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onServiceInfoChanged(true)
        // API 36+ no longer dispatches KEYCODE_BACK; hide overlays via predictive back.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isOverlaysVisible()) {
                        hideOverlays()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun applyServiceList(services: ArrayList<ServiceNowNext>) {
        serviceList.clear()
        servicesView?.adapter?.notifyDataSetChanged()
        serviceList.addAll(services)
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
        if (serviceList.isEmpty()) {
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
        if (Service.isMarker(serviceRef)) return
        val serviceInfo = serviceInfoForIntent()
        val streamingIntent =
            IntentFactory.getStreamServiceIntent(
                requireActivity(),
                serviceRef!!,
                title ?: "",
                bouquetRef,
                serviceInfo as? ServiceNowNext
            )
        val zapExtras =
            VideoPlayback.overlayExtrasForZap(title, serviceRef, bouquetRef)
        requireArguments().putString(TITLE, zapExtras.title)
        requireArguments().putString(SERVICE_REFERENCE, zapExtras.serviceRef)
        requireArguments().putString(BOUQUET_REFERENCE, zapExtras.bouquetRef)
        requireArguments().putSerializable(SERVICE_INFO, serviceInfo)
        (requireActivity() as VideoActivity).handleIntent(streamingIntent)

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
        val args = arguments
        if (args != null && args !== extras) {
            args.putString(TITLE, incoming.title)
            args.putString(SERVICE_REFERENCE, incoming.serviceRef)
            args.putString(BOUQUET_REFERENCE, incoming.bouquetRef)
            if (extras.containsKey(SERVICE_INFO)) {
                @Suppress("DEPRECATION")
                val serviceInfo = extras.get(SERVICE_INFO) as java.io.Serializable?
                args.putSerializable(SERVICE_INFO, serviceInfo)
            } else {
                args.remove(SERVICE_INFO)
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

        if ((titleChanged || refsChanged) && view != null && this::handler.isInitialized) {
            onServiceInfoChanged(true)
            if (isResumed) {
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
        if (bouquetRef.isNullOrEmpty() || activity == null) return
        if (!isAdded || view == null) return
        cancelLoad()
        val params = arrayListOf(NameValuePair("bRef", bouquetRef))
        loadJob =
            launchEpgNowNextLoad(params) { success, rows, errorText ->
                onEpgNowNextReady(success, rows, errorText)
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
        if (!isAdded) return
        if (!success) {
            val message = errorText ?: getString(R.string.get_content_error)
            Log.e(LOG_TAG, message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
        if (view == null) return

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
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun updateProgress() {
        if (view == null) return
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

    override fun onResume() {
        super.onResume()
        showOverlays()
        reload()
    }

    override fun onPause() {
        handler.removeCallbacks(autoHideRunnable)
        handler.removeCallbacks(issueReloadRunnable)
        cancelLoad()
        super.onPause()
    }

    fun autohide() {
        handler.removeCallbacks(autoHideRunnable)
        handler.postDelayed(autoHideRunnable, AUTOHIDE_DEFAULT_TIMEOUT.toLong())
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        hideOverlays()
    }

    fun showOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            requireActivity().isInPictureInPictureMode
        ) {
            hideOverlays()
            return
        }
        if (view == null) return
        handler.removeCallbacks(autoHideRunnable)
        updateViews()
        if (servicesViewVisible) {
            showZapOverlays()
        }
        fadeInView(overlayRoot)
        autohide()
    }

    fun hideOverlays() {
        if (view == null) return
        handler.removeCallbacks(autoHideRunnable)
        hideZapOverlays()
        fadeOutView(overlayRoot)
    }

    private fun showZapOverlays() {
        if (serviceList.isEmpty()) {
            hideZapOverlays()
            return
        }
        if (view == null) return
        val servicesView = this.servicesView
        if (servicesView != null) {
            servicesView.layoutManager!!.scrollToPosition(getCurrentServiceIndex())
            fadeInView(servicesView)
        }
        autohide()
    }

    private fun hideZapOverlays() {
        if (view == null) return
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

    protected fun isOverlaysVisible(): Boolean {
        val sdroot = requireView().findViewById<View>(R.id.overlay_root)
        return sdroot.visibility == View.VISIBLE
    }

    override fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                val progressView = requireView().findViewById<View>(R.id.video_load_progress)
                fadeInView(progressView)
            }

            MediaPlayer.Event.Playing -> {
                val progressView = requireView().findViewById<View>(R.id.video_load_progress)
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
        val row = serviceList[position]
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

        private val LOG_TAG: String = VideoOverlayFragment::class.java.simpleName

        var overlayAlpha: Float = 0.85f

        var seekStepSize: Float = 0.02f
    }
}
