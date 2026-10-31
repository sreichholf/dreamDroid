package net.reichholf.dreamdroid.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Carries out [AudioFocusPolicy] for [player] on [AudioManager]: holds audio focus while the
 * player plays and pauses it when the headphones are unplugged. Main thread only.
 */
class PlaybackAudioFocus(private val context: Context, private val player: VideoPlayer) {
    private val policy = AudioFocusPolicy()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var job: Job? = null
    private var noisyRegistered = false

    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(::onAudioFocusChange, Handler(Looper.getMainLooper()))
            .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                run(policy.onBecomingNoisy())
            }
        }
    }

    /** Follows the player's state in [scope] until [release]. */
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            player.state.map { it.isPlaying }.distinctUntilChanged().collect { playing ->
                run(policy.onPlayingChanged(playing))
            }
        }
    }

    fun onUserPlayPause() = run(policy.onUserPlayPause())

    fun release() {
        job?.cancel()
        job = null
        run(policy.onReleased())
    }

    private fun onAudioFocusChange(change: Int) {
        val focusChange = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusChange.Gain
            AudioManager.AUDIOFOCUS_LOSS -> FocusChange.Loss
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LossTransient
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LossTransientCanDuck
            else -> return
        }
        run(policy.onFocusChange(focusChange))
    }

    private fun run(commands: List<FocusCommand>) {
        commands.forEach { command ->
            when (command) {
                FocusCommand.Request -> {
                    val result = audioManager.requestAudioFocus(focusRequest)
                    run(policy.onRequestResult(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
                }

                FocusCommand.Abandon -> audioManager.abandonAudioFocusRequest(focusRequest)

                FocusCommand.Play -> player.play()

                FocusCommand.Pause -> player.pause()

                is FocusCommand.ListenNoisy -> listenNoisy(command.listen)
            }
        }
    }

    private fun listenNoisy(listen: Boolean) {
        if (listen == noisyRegistered) return
        noisyRegistered = listen
        if (listen) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.unregisterReceiver(noisyReceiver)
        }
    }
}
