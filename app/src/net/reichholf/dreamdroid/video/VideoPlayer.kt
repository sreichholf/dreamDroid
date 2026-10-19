package net.reichholf.dreamdroid.video

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.di.ApplicationScope

/**
 * The integrated player: plays a [PlayRequest] on its [Engine], opens a dropped live stream
 * again ([LiveReconnect]), and hands a stream Media3 cannot decode to VLC. Call it from the
 * main thread; events reach the listener there too.
 */
@Singleton
class VideoPlayer(
    private val vlc: PlayerEngine,
    private val media3: PlayerEngine,
    private val scope: CoroutineScope
) {
    @Inject
    constructor(
        vlc: VlcEngine,
        media3: Media3Engine,
        @ApplicationScope scope: CoroutineScope
    ) : this(vlc as PlayerEngine, media3 as PlayerEngine, scope)

    private var engine: PlayerEngine = vlc
    private var views: PlayerViews? = null
    private var listener: ((PlayerEvent) -> Unit)? = null

    /** The stream [playUri] asked for last; a reconnect or the fallback opens it again. */
    private var request: PlayRequest? = null
    private var reconnect = LiveReconnect()
    private var reconnectJob: Job? = null

    /**
     * True once a hardware decoder showed no picture: the device's decoder fails on this
     * receiver's streams, so later streams of the session start in software right away.
     */
    private var softwareVideo = false

    private val stateFlow = MutableStateFlow(PlayerState())

    /** The playing engine's state. */
    val state: StateFlow<PlayerState> = stateFlow.asStateFlow()
    private var mirrorJob: Job? = null

    init {
        use(vlc, force = true)
    }

    fun setEventListener(listener: ((PlayerEvent) -> Unit)?) {
        this.listener = listener
    }

    fun attach(views: PlayerViews) {
        this.views = views
        engine.attach(views)
    }

    /** Detaches the views and stops the stream; [release] also frees the decoder. */
    fun detach() {
        reconnectJob?.cancel()
        views = null
        engine.detach()
    }

    fun release() {
        reconnectJob?.cancel()
        request = null
        engine.release()
    }

    fun playUri(requested: PlayRequest) {
        reconnectJob?.cancel()
        val request = if (softwareVideo) requested.inSoftware() else requested
        this.request = request
        reconnect = LiveReconnect()
        val target = VideoPlayback.engineFor(request.engine, request.url.substringBefore("://"))
        use(if (target == Engine.Media3) media3 else vlc)
        engine.start(request)
    }

    fun play() = engine.play()

    fun setPosition(position: Float) = engine.setPosition(position)

    fun slower() = engine.slower()

    fun audioTracks(): List<MediaTrack>? = engine.audioTracks()

    fun subtitleTracks(): List<MediaTrack>? = engine.subtitleTracks()

    fun setAudioTrack(id: Int) = engine.setAudioTrack(id)

    fun setSubtitleTrack(id: Int) = engine.setSubtitleTrack(id)

    /** Makes [target] the playing engine: frees the other one and moves the views over. */
    private fun use(target: PlayerEngine, force: Boolean = false) {
        if (target === engine && !force) return
        if (target !== engine) {
            engine.setEventListener(null)
            engine.release()
            engine = target
            views?.let(target::attach)
        }
        target.setEventListener(::onEngineEvent)
        mirrorJob?.cancel()
        mirrorJob = scope.launch(Dispatchers.Main.immediate) {
            target.state.collect { stateFlow.value = it }
        }
    }

    private fun onEngineEvent(event: PlayerEvent) {
        // An error is often followed by the end of the same stream; one reconnect covers both.
        if (reconnectJob?.isActive == true &&
            (event is PlayerEvent.Failed || event == PlayerEvent.Ended)
        ) {
            return
        }
        when (event) {
            PlayerEvent.Progress -> reconnect.onPlayed()

            is PlayerEvent.Failed -> {
                if (event.unsupported && fallBackToVlc()) return
                if (reconnectLive()) return
            }

            PlayerEvent.Ended -> if (reconnectLive()) return

            PlayerEvent.NoVideo -> {
                decodeInSoftware()
                return
            }

            else -> Unit
        }
        listener?.invoke(event)
    }

    private fun fallBackToVlc(): Boolean {
        val request = request ?: return false
        if (engine !== media3) return false
        Log.i(LOG_TAG, "Media3 cannot decode the stream, playing it with VLC")
        val fallback = request.copy(engine = Engine.Vlc)
        this.request = fallback
        use(vlc)
        vlc.start(fallback)
        listener?.invoke(PlayerEvent.FellBackToVlc)
        return true
    }

    /** Plays the stream again with VLC's software decoder, and later streams start there. */
    private fun decodeInSoftware() {
        val request = request ?: return
        Log.i(LOG_TAG, "No picture from the hardware decoder, decoding in software")
        softwareVideo = true
        val software = request.inSoftware()
        this.request = software
        use(vlc)
        vlc.start(software)
        listener?.invoke(PlayerEvent.SoftwareDecoding)
    }

    private fun PlayRequest.inSoftware() = copy(engine = Engine.Vlc, hwAccelFlags = 0)

    /**
     * Opens a live stream that failed or ended again after [LiveReconnect]'s wait. False when
     * the stream is not live or the attempts ran out; the event then reaches the listener.
     */
    private fun reconnectLive(): Boolean {
        val request = request?.takeIf { it.live } ?: return false
        val delayMs = reconnect.onFailure() ?: return false
        Log.i(LOG_TAG, "Live stream dropped, reconnecting in $delayMs ms")
        reconnectJob = scope.launch(Dispatchers.Main.immediate) {
            delay(delayMs)
            engine.start(request)
        }
        return true
    }

    private companion object {
        const val LOG_TAG = "VideoPlayer"
    }
}
