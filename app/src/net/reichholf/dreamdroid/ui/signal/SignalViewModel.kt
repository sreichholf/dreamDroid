package net.reichholf.dreamdroid.ui.signal

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.loadSignal
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

private const val TAG = "SignalViewModel"
private const val MAX_SNR_DB = 20
private const val MIN_SNR_DB = 5
private const val MAX_DELAY = 1000
private const val MIN_DELAY = 150

/**
 * Signal meter poll and acoustic tone. [SignalUiState] stays the model [SignalScreen] renders.
 *
 * [savedStateHandle] is accepted so the default factory can construct this ViewModel.
 * This screen has no rememberSaveable fields, so no keys are written.
 */
class SignalViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val uiState: SignalUiState = SignalUiState()

    var toolbarTitle by mutableStateOf("")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    private val pollGate = SignalPollGate()
    private var isUpdating: Boolean = false
    private var startTime: Long = 0L
    private var loadJob: Job? = null
    private var acousticJob: Job? = null

    init {
        toolbarTitle = baseTitle()
    }

    fun startPolling() {
        pollGate.start()
        isUpdating = false
        if (uiState.enabled) {
            reload()
            if (uiState.acousticFeedback) {
                startAcoustic()
            }
        }
    }

    /**
     * Stops the poll and the tone.
     *
     * [clearMeter] is true when the meter is switched off or mutations are blocked.
     * Leaving the tools hub passes false so the last reading is still there on return.
     */
    fun stopPolling(clearMeter: Boolean) {
        stopAcoustic()
        pollGate.stop()
        loadJob?.cancel()
        loadJob = null
        isUpdating = false
        if (clearMeter) {
            uiState.clearMeter()
        }
        toolbarTitle = baseTitle()
    }

    fun onEnabledChange(enabled: Boolean) {
        if (SessionConnectionHolder.shared.status.value.blocksMutations) {
            return
        }
        if (enabled) {
            startPolling()
        } else {
            stopPolling(clearMeter = true)
        }
    }

    fun onAcousticChange(acoustic: Boolean) {
        if (!acoustic) {
            stopAcoustic()
            return
        }
        if (uiState.enabled && pollGate.active) {
            startAcoustic()
        }
    }

    fun consumeError() {
        errorText = null
    }

    override fun onCleared() {
        stopPolling(clearMeter = false)
        super.onCleared()
    }

    private fun reload() {
        if (!pollGate.active) {
            return
        }
        if (SessionConnectionHolder.shared.status.value.blocksMutations) {
            return
        }
        startTime = System.currentTimeMillis()
        toolbarTitle = loadingTitle()
        if (isUpdating) {
            return
        }
        isUpdating = true
        val generation = pollGate.nextLoadGeneration()
        loadJob?.cancel()
        val app = getApplication<Application>()
        loadJob = viewModelScope.launch {
            val result = loadSignal(app)
            if (!pollGate.isCurrent(generation)) {
                return@launch
            }
            isUpdating = false
            toolbarTitle = baseTitle()
            if (!uiState.enabled) {
                return@launch
            }
            if (!result.success || result.signal == null) {
                uiState.enabled = false
                stopPolling(clearMeter = true)
                errorText = result.errorText?.takeIf { it.isNotEmpty() }
                return@launch
            }
            applySignal(result.signal)
            if (pollGate.isCurrent(generation)) {
                reload()
            }
        }
    }

    private fun applySignal(signal: Signal) {
        val stopTime = System.currentTimeMillis()
        Log.w(TAG, "request & parsing took: ${stopTime - startTime}ms")
        uiState.apply(signal, MIN_SNR_DB.toDouble())
    }

    private fun startAcoustic() {
        stopAcoustic()
        acousticJob = viewModelScope.launch {
            while (pollGate.active) {
                val db = uiState.snrDb
                val freq = (1650 * db * db) / 1000 + 200
                launch(Dispatchers.IO) { playAcousticTone(freq) }
                var delayMs = MIN_DELAY * (MAX_SNR_DB.toDouble().pow(3) / db.pow(3))
                if (delayMs > MAX_DELAY) {
                    delayMs = MAX_DELAY.toDouble()
                }
                if (!pollGate.active) {
                    break
                }
                delay(delayMs.toLong())
            }
        }
    }

    private fun stopAcoustic() {
        acousticJob?.cancel()
        acousticJob = null
    }

    private fun baseTitle(): String = getApplication<Application>().getString(R.string.signal_meter)

    private fun loadingTitle(): String {
        val app = getApplication<Application>()
        return "${app.getString(R.string.signal_meter)} - ${app.getString(R.string.loading)}"
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
