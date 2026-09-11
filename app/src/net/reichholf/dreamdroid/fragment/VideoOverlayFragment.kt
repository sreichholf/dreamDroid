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
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Movie
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.fragment.EpgDetailDialog
import net.reichholf.dreamdroid.tv.fragment.MovieDetailDialog
import net.reichholf.dreamdroid.ui.services.movieFromExtendedHashMap
import net.reichholf.dreamdroid.ui.services.movieToExtendedHashMap
import net.reichholf.dreamdroid.ui.services.serviceNowNextFromExtendedHashMap
import net.reichholf.dreamdroid.ui.services.serviceNowNextToExtendedHashMap
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import net.reichholf.dreamdroid.ui.video.showEpgDetail
import net.reichholf.dreamdroid.ui.video.showMovieDetail
import net.reichholf.dreamdroid.ui.video.bindVideoOverlayScreen
import net.reichholf.dreamdroid.video.VLCPlayer
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

    @JvmField
    val TITLE: String = "title"

    @JvmField
    val SERVICE_INFO: String = "serviceInfo"

    @JvmField
    val BOUQUET_REFERENCE: String = "bouquetRef"

    @JvmField
    val SERVICE_REFERENCE: String = "serviceRef"

    protected var mSurfaceHeight: Int = 0
    protected var mSurfaceWidth: Int = 0

    protected var mTitle: String? = null
    protected var mServiceRef: String? = null
    protected var mBouquetRef: String? = null

    protected lateinit var mServiceList: ArrayList<ServiceNowNext>
    protected var mCurrentService: ServiceNowNext? = null
    protected var mMovie: EnigmaMovie? = null

    protected lateinit var mHandler: Handler
    protected lateinit var mAutoHideRunnable: Runnable
    protected lateinit var mIssueReloadRunnable: Runnable

    protected var mItemClickSupport: ItemClickSupport? = null

    protected var mOverlayRoot: View? = null
    protected var mServicesView: RecyclerView? = null
    protected var mComposeOverlay: ComposeView? = null

    protected val mOverlayUiState: VideoOverlayUiState = VideoOverlayUiState()

    private var mGestureDector: GestureDetectorCompat? = null
    private lateinit var mAudioManager: AudioManager
    private var mAudioMaxVol: Int = 0
    private var mVolume: Float = 0f
    private var mServicesViewVisible: Boolean = false

    private var mLoadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        retainInstance = true
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
        mTitle = requireArguments().getString(TITLE)
        mServiceRef = requireArguments().getString(SERVICE_REFERENCE)
        mBouquetRef = requireArguments().getString(BOUQUET_REFERENCE)
        mServiceList = ArrayList()
        @Suppress("DEPRECATION")
        val serviceInfoHash = requireArguments().get(SERVICE_INFO) as ExtendedHashMap?
        if (serviceInfoHash != null) {
            if (serviceInfoHash.containsKey(Movie.KEY_FILE_NAME)) {
                mMovie = movieFromExtendedHashMap(serviceInfoHash)
            } else {
                mCurrentService = serviceNowNextFromExtendedHashMap(serviceInfoHash)
            }
        }
        mHandler = Handler(Looper.getMainLooper())
        mServicesViewVisible = false
        mAutoHideRunnable = Runnable { hideOverlays() }
        mIssueReloadRunnable = Runnable { reload() }

        mAudioManager =
            requireActivity().applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioMaxVol = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        mVolume = -1f

        autohide()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.video_player_overlay, container, false)
        mOverlayRoot = view.findViewById(R.id.overlay_root)
        mServicesView = view.findViewById(R.id.servicelist)
        mComposeOverlay = view.findViewById(R.id.compose_overlay)
        mComposeOverlay!!.bindVideoOverlayScreen(
            state = mOverlayUiState,
            onPlay = { onPlay() },
            onRewind = { onRewind() },
            onForward = { onForward() },
            onInfo = { onInfo() },
            onList = { onList() },
            onAudio = { onSelectAudioTrack() },
            onSubtitle = { onSelectSubtitleTrack() },
            onSeekChange = { progress -> seek(progress) },
        )
        mOverlayUiState.onChoiceAction = { actionId, dialogTag ->
            onDialogAction(actionId, null, dialogTag)
        }
        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val servicesView = mServicesView
        if (servicesView != null) {
            if (DreamDroid.isTV(requireContext())) {
                val gridView = servicesView as HorizontalGridView
                gridView.setNumRows(1)
            } else {
                servicesView.layoutManager = GridLayoutManager(requireActivity(), 1)
            }
            if (mServiceList.isEmpty()) {
                mOverlayUiState.showListButton = false
            }
            servicesView.addItemDecoration(
                SpacesItemDecoration(
                    requireActivity().resources.getDimensionPixelSize(R.dimen.recylcerview_content_margin),
                ),
            )
            mItemClickSupport = ItemClickSupport.addTo(servicesView)
            mItemClickSupport!!.setOnItemClickListener(this)

            servicesView.adapter = ServiceAdapter(requireActivity(), mServiceList)
            servicesView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            autohide()
                        } else {
                            mHandler.removeCallbacks(mAutoHideRunnable)
                        }
                    }
                },
            )
            mServicesViewVisible = servicesView.visibility == View.VISIBLE
        }

        mGestureDector =
            GestureDetectorCompat(
                requireActivity(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float,
                    ): Boolean {
                        if (e1 == null) return true
                        val isGesturesEnabled =
                            PreferenceManager.getDefaultSharedPreferences(requireContext())
                                .getBoolean(DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES, true)
                        if (!isGesturesEnabled) return true

                        Log.d(LOG_TAG, String.format("distanceY=%s, DeltaY=%s", distanceY, e1.y - e2.y))
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
                },
            )

        requireActivity().findViewById<View>(R.id.overlay).setOnTouchListener { _, event ->
            val metrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
            if (mSurfaceHeight == 0) {
                mSurfaceHeight = min(metrics.widthPixels, metrics.heightPixels)
            }
            if (mSurfaceWidth == 0) {
                mSurfaceWidth = max(metrics.widthPixels, metrics.heightPixels)
            }
            mGestureDector!!.onTouchEvent(event)
            true
        }
    }

    protected fun onRewind() {
        val p = VLCPlayer.get()!!
        p.setPosition(max(0.0f, p.getPosition() - sSeekStepSize))
        autohide()
    }

    protected fun onForward() {
        val p = VLCPlayer.get()!!
        p.setPosition(max(0.0f, p.getPosition() + sSeekStepSize))
        autohide()
    }

    protected fun onPlay() {
        VLCPlayer.get()!!.play()
        autohide()
    }

    fun onUpdateButtons() {
        val player = VLCPlayer.get() ?: return
        mOverlayUiState.showAudioButton = player.getAudioTracksCount() > 0
        mOverlayUiState.showSubtitleButton = player.getSubtitleTracksCount() > 0
    }

    private fun onSelectAudioTrack() {
        val player = VLCPlayer.getMediaPlayer()!!
        showTrackSelection(getString(R.string.audio_tracks), player.audioTracks, DIALOG_TAG_AUDIO_TRACK)
    }

    private fun onSelectSubtitleTrack() {
        val player = VLCPlayer.getMediaPlayer()!!
        showTrackSelection(getString(R.string.subtitles), player.spuTracks, DIALOG_TAG_SUBTITLE_TRACK)
    }

    private fun onInfo() {
        if (mMovie == null && mCurrentService == null) return

        if (mMovie != null) {
            if (DreamDroid.isTV(requireContext())) {
                MovieDetailDialog.newInstance(mMovie!!)
                    .show(parentFragmentManager, "details_dialog_tv")
            } else {
                mOverlayUiState.showMovieDetail(mMovie!!)
            }
            return
        }

        var event = mCurrentService!!.now
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
                    mCurrentService!!.serviceReference,
                    mCurrentService!!.serviceName,
                    "",
                    "",
                    "",
                )
        }
        if (DreamDroid.isTV(requireContext())) {
            EpgDetailDialog.newInstance(event)
                .show(parentFragmentManager, "details_dialog_tv")
        } else {
            mOverlayUiState.showEpgDetail(requireContext(), event)
        }
    }

    private fun onList() {
        if (!mServicesViewVisible) {
            mServicesViewVisible = true
            showZapOverlays()
        } else {
            hideZapOverlays()
            mServicesViewVisible = false
        }
    }

    private fun showTrackSelection(
        title: String,
        descriptions: Array<MediaPlayer.TrackDescription>?,
        dialogTag: String,
    ) {
        // this should actually never be true, but just to be sure we do it anyways
        if (descriptions == null || descriptions.isEmpty()) {
            Toast.makeText(context, R.string.no_tracks, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = descriptions.map { it.name }
        val ids = IntArray(descriptions.size) { idx -> descriptions[idx].id }
        mOverlayUiState.showChoice(title, labels, ids, dialogTag)
    }

    private fun onVolumeTouch(distanceY: Float) {
        val delta = (distanceY / mSurfaceHeight) * 100
        var currentVolume =
            mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / mAudioMaxVol.toFloat() * 100
        if (mVolume > 0) {
            currentVolume = mVolume
        }

        currentVolume += delta
        currentVolume = max(min(currentVolume, 100f), 0f)
        mVolume = currentVolume
        setVolume((currentVolume / 100 * mAudioMaxVol).toInt())
    }

    protected fun setVolume(volume: Int) {
        val currentVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume != currentVol) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun onBrightnessTouch(distanceY: Float) {
        val delta = distanceY / mSurfaceHeight
        val window = requireActivity().window
        val layoutParams = window.attributes
        layoutParams.screenBrightness = min(max(layoutParams.screenBrightness + delta, 0.01f), 1f)
        window.attributes = layoutParams
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onServiceInfoChanged(true)
    }

    private fun applyServiceList(services: ArrayList<ServiceNowNext>) {
        mServiceList.clear()
        mServicesView?.adapter?.notifyDataSetChanged()
        mServiceList.addAll(services)
        for (service in mServiceList) {
            if (service.serviceReference == mServiceRef) {
                val oldService = mCurrentService
                mCurrentService = service
                mMovie = null
                val eventid = mCurrentService!!.now?.eventId ?: "-1"
                val oldEventId = oldService?.now?.eventId ?: "-2"
                if (oldService == null || eventid != oldEventId) {
                    onServiceInfoChanged(false)
                }
            }
            mServicesView?.adapter?.notifyDataSetChanged()
        }
        if (mServiceList.isEmpty()) {
            mOverlayUiState.showListButton = false
            hideZapOverlays()
        } else {
            mOverlayUiState.showListButton = true
            if (isOverlaysVisible() && mServicesViewVisible) {
                showZapOverlays()
            }
        }
    }

    private fun zap() {
        if (Service.isMarker(mServiceRef)) return
        val serviceInfoHash = serviceInfoForIntent()
        val streamingIntent =
            IntentFactory.getStreamServiceIntent(
                requireActivity(),
                mServiceRef!!,
                mTitle ?: "",
                mBouquetRef,
                serviceInfoHash,
            )
        requireArguments().putString(TITLE, mTitle)
        requireArguments().getString(SERVICE_REFERENCE, mServiceRef)
        requireArguments().getString(BOUQUET_REFERENCE, mBouquetRef)
        requireArguments().putSerializable(SERVICE_INFO, serviceInfoHash)
        (requireActivity() as VideoActivity).handleIntent(streamingIntent)

        onServiceInfoChanged(true)
    }

    private fun serviceInfoForIntent(): ExtendedHashMap? {
        if (mMovie != null) {
            return movieToExtendedHashMap(mMovie!!)
        }
        if (mCurrentService != null) {
            return serviceNowNextToExtendedHashMap(mCurrentService!!)
        }
        return null
    }

    private fun getPreviousServiceInfo(): ServiceNowNext? {
        var index = getCurrentServiceIndex()
        if (index < 0) return null
        index = if (index == 0) mServiceList.size - 1 else index - 1
        return mServiceList[index]
    }

    private fun previous() {
        val serviceInfo = getPreviousServiceInfo() ?: return
        mCurrentService = serviceInfo
        mMovie = null
        mServiceRef = mCurrentService!!.serviceReference
        mTitle = mCurrentService!!.serviceName
        zap()
    }

    private fun getNextServiceInfo(): ServiceNowNext? {
        var index = getCurrentServiceIndex()
        if (index < 0) return null
        index++
        if (index >= mServiceList.size - 1) {
            index = 0
        }
        return mServiceList[index]
    }

    private fun next() {
        val serviceInfo = getNextServiceInfo() ?: return
        mCurrentService = serviceInfo
        mMovie = null
        mServiceRef = mCurrentService!!.serviceReference
        mTitle = mCurrentService!!.serviceName
        zap()
    }

    private fun getCurrentServiceIndex(): Int {
        if (mServiceList.isEmpty()) return -1
        var idx = 0
        for (service in mServiceList) {
            if (service.serviceReference == mServiceRef) return idx
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
        if (mCurrentService == null && mMovie == null) return
        mHandler.removeCallbacks(mIssueReloadRunnable)
        val now = mCurrentService?.now
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
            mHandler.postDelayed(mIssueReloadRunnable, delay)
        } else {
            Log.i(LOG_TAG, "No Eventinfo present, will update in 5 Minutes!")
            mHandler.postDelayed(mIssueReloadRunnable, 300000)
        }
    }

    fun reload() {
        if (mBouquetRef.isNullOrEmpty() || activity == null) return
        if (!isAdded || view == null) return
        cancelLoad()
        val params = arrayListOf(NameValuePair("bRef", mBouquetRef))
        mLoadJob =
            launchEpgNowNextLoad(params) { success, rows, errorText ->
                onEpgNowNextReady(success, rows, errorText)
            }
    }

    private fun cancelLoad() {
        mLoadJob?.cancel()
        mLoadJob = null
    }

    private fun onEpgNowNextReady(
        success: Boolean,
        rows: List<ServiceNowNext>,
        @Suppress("UNUSED_PARAMETER") errorText: String?,
    ) {
        if (!isAdded) return
        if (!success) return
        applyServiceList(ArrayList(rows))
    }

    private fun seek(pos: Int) {
        val player = VLCPlayer.get() ?: return
        val fpos = pos.toFloat()
        var length = player.getLength()
        length = if (length > 0) length / 1000 else sFakeLength.toLong()
        player.setPosition(fpos / length)
    }

    private fun isRecording(): Boolean {
        val isDreamboxRecording = mMovie != null
        return VLCPlayer.get()!!.isSeekable() || isDreamboxRecording
    }

    private fun updateViews() {
        if (view == null) return

        mOverlayUiState.title = mTitle ?: ""
        val player = VLCPlayer.get()
        mOverlayUiState.showPvrControls = player != null && player.isSeekable()

        if (mMovie != null || mCurrentService != null) {
            mOverlayUiState.showInfoButton = true
            if (isRecording()) {
                val movieTitle = mMovie?.title
                mOverlayUiState.title =
                    if (!movieTitle.isNullOrEmpty()) movieTitle else (mTitle ?: "")
            } else if (mCurrentService != null) {
                val serviceName = mCurrentService!!.serviceName
                mOverlayUiState.title =
                    if (serviceName.isNotEmpty()) serviceName else (mTitle ?: "")
                val now = mCurrentService!!.now
                mOverlayUiState.nowStart = now?.startTimeReadable ?: ""
                mOverlayUiState.nowTitle = now?.title ?: ""
                mOverlayUiState.nowDuration = now?.durationReadable ?: ""
                mOverlayUiState.showNow = true
            }

            val nextEvent = mCurrentService?.next
            val next = nextEvent?.title
            val hasNext = !next.isNullOrEmpty()
            if (hasNext) {
                mOverlayUiState.nextStart = nextEvent?.startTimeReadable ?: ""
                mOverlayUiState.nextTitle = nextEvent?.title ?: ""
                mOverlayUiState.nextDuration = nextEvent?.durationReadable ?: ""
                mOverlayUiState.hasNext = true
            } else {
                mOverlayUiState.hasNext = false
            }
        } else {
            mOverlayUiState.showNow = false
            mOverlayUiState.hasNext = false
            mOverlayUiState.showInfoButton = false
        }
        updateProgress()
        mServicesView?.adapter?.notifyDataSetChanged()
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun updateProgress() {
        if (view == null) return
        val player = VLCPlayer.get()
        val isSeekable = player != null && player.isSeekable()
        mOverlayUiState.seekable = isSeekable
        var len = -1L
        var cur = -1L
        if (mMovie != null || mCurrentService != null) {
            if (isRecording()) {
                var duration = if (player != null) player.getLength() / 1000 else 0L
                if (duration <= 0) {
                    val textLen =
                        if (mMovie != null && !mMovie!!.length.isNullOrEmpty()) {
                            mMovie!!.length
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
                    mOverlayUiState.nowStart = DateTime.minutesAndSeconds(pos.toInt())
                    mOverlayUiState.nowTitle = mMovie?.serviceName ?: ""
                    mOverlayUiState.nowDuration = DateTime.minutesAndSeconds(duration.toInt())
                    mOverlayUiState.showNow = true
                } else {
                    mOverlayUiState.showNow = false
                }
                mOverlayUiState.hasNext = false
            } else if (mCurrentService?.now != null) {
                val now = mCurrentService!!.now!!
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
            len = sFakeLength.toLong()
            cur = (len * player.getPosition()).toLong()
        }

        if (len > 0 && cur >= 0) {
            mOverlayUiState.progressEnabled = true
            mOverlayUiState.progressMax = len.toInt()
            mOverlayUiState.progress = cur.toInt()
        } else {
            mOverlayUiState.progressEnabled = false
            mOverlayUiState.progressMax = 0
            mOverlayUiState.progress = 0
        }
    }

    override fun onResume() {
        super.onResume()
        showOverlays()
        reload()
    }

    override fun onPause() {
        mHandler.removeCallbacks(mAutoHideRunnable)
        mHandler.removeCallbacks(mIssueReloadRunnable)
        cancelLoad()
        super.onPause()
    }

    fun autohide() {
        mHandler.removeCallbacks(mAutoHideRunnable)
        mHandler.postDelayed(mAutoHideRunnable, AUTOHIDE_DEFAULT_TIMEOUT.toLong())
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        hideOverlays()
    }

    fun showOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && requireActivity().isInPictureInPictureMode) {
            hideOverlays()
            return
        }
        if (view == null) return
        mHandler.removeCallbacks(mAutoHideRunnable)
        updateViews()
        if (mServicesViewVisible) {
            showZapOverlays()
        }
        fadeInView(mOverlayRoot)
        autohide()
    }

    fun hideOverlays() {
        if (view == null) return
        mHandler.removeCallbacks(mAutoHideRunnable)
        hideZapOverlays()
        fadeOutView(mOverlayRoot)
    }

    private fun showZapOverlays() {
        if (mServiceList.isEmpty()) {
            hideZapOverlays()
            return
        }
        if (view == null) return
        val servicesView = mServicesView
        if (servicesView != null) {
            servicesView.layoutManager!!.scrollToPosition(getCurrentServiceIndex())
            fadeInView(servicesView)
        }
        autohide()
    }

    private fun hideZapOverlays() {
        if (view == null) return
        fadeOutView(mServicesView)
    }

    private fun fadeInView(v: View?) {
        if (v == null || v.visibility == View.VISIBLE) return
        v.visibility = View.VISIBLE
        v.alpha = 0.0f
        v.animate().alpha(sOverlayAlpha).setListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.alpha = sOverlayAlpha
                }
            },
        )
    }

    private fun fadeOutView(v: View?) {
        if (v == null || v.visibility == View.GONE) return
        v.animate().alpha(0.0f).setListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.visibility = View.GONE
                }
            },
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
        val row = mServiceList[position]
        val serviceRef = row.serviceReference
        if (Service.isMarker(serviceRef)) return
        mCurrentService = row
        mMovie = null
        mServiceRef = serviceRef
        mTitle = row.serviceName
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
                    onRewind()
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
        const val DIALOG_TAG_AUDIO_TRACK: String = "dialog_audio_track"
        const val DIALOG_TAG_SUBTITLE_TRACK: String = "dialog_subtitle_track"

        private const val AUTOHIDE_DEFAULT_TIMEOUT: Int = 7000
        private const val sFakeLength: Int = 10000

        private val LOG_TAG: String = VideoOverlayFragment::class.java.simpleName

        @JvmField
        var sOverlayAlpha: Float = 0.85f

        @JvmField
        var sSeekStepSize: Float = 0.02f
    }
}
