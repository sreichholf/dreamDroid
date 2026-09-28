package net.reichholf.dreamdroid.ui.signal

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

private const val MAX_SNR_DB = 20.0
private const val MAX_DELAY_MS = 1000.0
private const val MIN_DELAY_MS = 150.0

/**
 * Signal meter. [signal] is the last reading, null when the meter is cleared. [polling] is
 * true while the meter requests readings; [blocked] mirrors the session's
 * `blocksMutations`, which stops the meter.
 */
data class SignalUiState(
    val enabled: Boolean = true,
    val acousticFeedback: Boolean = false,
    val signal: Signal? = null,
    val polling: Boolean = false,
    val blocked: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = if (polling) {
            UiText.Resource(
                R.string.title_with_status,
                listOf(UiText.Resource(R.string.signal_meter), UiText.Resource(R.string.loading))
            )
        } else {
            UiText.Resource(R.string.signal_meter)
        }

    /** SNR for the acoustic tone, never below [Signal.MIN_SNR_DB]. */
    val snrDb: Double
        get() = max(signal?.snrDb ?: Signal.MIN_SNR_DB, Signal.MIN_SNR_DB)
}

/**
 * Polls `/web/signal` back to back while the meter is shown, enabled, and the session is
 * online, and plays the acoustic tone alongside. A failed reading switches the meter off.
 */
@HiltViewModel
class SignalViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val receiver: ReceiverRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SignalUiState(
            enabled = savedStateHandle[KEY_ENABLED] ?: true,
            acousticFeedback = savedStateHandle[KEY_ACOUSTIC] ?: false,
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    private var shown = false
    private var pollJob: Job? = null
    private var toneJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
                updatePolling()
                if (blocked) {
                    clearMeter()
                }
            }
        }
    }

    /** The meter is on screen and started: poll while enabled and online. */
    fun onShown() {
        shown = true
        updatePolling()
    }

    /** The meter left the screen or stopped: stop polling, keep the last reading. */
    fun onHidden() {
        shown = false
        updatePolling()
    }

    /** Ignored while [SignalUiState.blocked]; switching off clears the meter. */
    fun onEnabledChange(enabled: Boolean) {
        if (_uiState.value.blocked) {
            return
        }
        savedStateHandle[KEY_ENABLED] = enabled
        _uiState.update { it.copy(enabled = enabled) }
        updatePolling()
        if (!enabled) {
            clearMeter()
        }
    }

    fun onAcousticChange(acoustic: Boolean) {
        savedStateHandle[KEY_ACOUSTIC] = acoustic
        _uiState.update { it.copy(acousticFeedback = acoustic) }
        updatePolling()
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun clearMeter() {
        _uiState.update { it.copy(signal = null) }
    }

    private fun updatePolling() {
        val state = _uiState.value
        val poll = shown && state.enabled && !state.blocked
        if (!poll) {
            pollJob?.cancel()
            pollJob = null
            _uiState.update { it.copy(polling = false) }
        } else if (pollJob == null) {
            pollJob = viewModelScope.launch { poll() }
        }
        val tone = poll && state.acousticFeedback
        if (!tone) {
            toneJob?.cancel()
            toneJob = null
        } else if (toneJob == null) {
            toneJob = viewModelScope.launch { playTones() }
        }
    }

    private suspend fun poll() {
        _uiState.update { it.copy(polling = true) }
        while (true) {
            val response = receiver.signal()
            val signal = response.value?.takeUnless { it.isEmpty() }
            if (signal == null) {
                val message = response.error?.contentErrorText()
                    ?: UiText.Resource(R.string.error_parsing)
                savedStateHandle[KEY_ENABLED] = false
                _uiState.update {
                    it.copy(enabled = false, signal = null, polling = false, userMessage = message)
                }
                pollJob = null
                updatePolling()
                return
            }
            _uiState.update { it.copy(signal = signal) }
        }
    }

    /** A short beep whose pitch rises and whose interval shrinks with the SNR. */
    private suspend fun playTones() = coroutineScope {
        while (true) {
            val db = _uiState.value.snrDb
            val freq = (1650 * db * db) / 1000 + 200
            launch(Dispatchers.IO) { playAcousticTone(freq) }
            delay(min(MIN_DELAY_MS * MAX_SNR_DB.pow(3) / db.pow(3), MAX_DELAY_MS).toLong())
        }
    }

    private companion object {
        const val KEY_ENABLED = "signal_enabled"
        const val KEY_ACOUSTIC = "signal_acoustic"
    }
}

private suspend fun playAcousticTone(freqOfTone: Double) {
    val duration = 0.075
    val sampleRate = 44100
    val numSamples = ceil(duration * sampleRate).toInt()
    val sample = DoubleArray(numSamples)
    val generatedSnd = ByteArray(2 * numSamples)
    val audioTrack = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(numSamples * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    } catch (_: Exception) {
        return
    }

    for (i in 0 until numSamples) {
        sample[i] = sin(freqOfTone * 2 * Math.PI * i / sampleRate)
    }

    val ramp = numSamples / 2
    var idx = 0
    for (i in 0 until numSamples) {
        val dVal = sample[i]
        val valShort = when {
            i < ramp -> (dVal * 32767 * i / ramp).toInt().toShort()
            i < numSamples - ramp -> (dVal * 32767).toInt().toShort()
            else -> (dVal * 32767 * (numSamples - i) / ramp).toInt().toShort()
        }
        generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
        generatedSnd[idx++] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
    }

    try {
        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.play()
    } catch (_: Exception) {
        audioTrack.release()
        return
    }

    var head = 0
    while (head < numSamples && coroutineContext.isActive) {
        head = audioTrack.playbackHeadPosition
    }
    audioTrack.release()
}
