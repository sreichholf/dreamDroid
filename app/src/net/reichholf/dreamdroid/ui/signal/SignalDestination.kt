package net.reichholf.dreamdroid.ui.signal

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.loadSignal
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sin

private const val TAG = "SignalDestination"
private const val MAX_SNR_DB = 20
private const val MIN_SNR_DB = 5
private const val MAX_DELAY = 1000
private const val MIN_DELAY = 150

/**
 * Phase 2.7c: Signal meter as a direct Compose NavHost destination (no nested Fragment).
 */
@Composable
fun SignalDestination(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = remember { SignalUiState() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var isUpdating by remember { mutableStateOf(false) }
    var signalGeneration by remember { mutableIntStateOf(0) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var startTime by remember { mutableStateOf(0L) }

    val baseTitle = context.getString(R.string.signal_meter)

    fun setToolbarTitle(title: String) {
        val activity = context as? AppCompatActivity ?: return
        activity.title = title
    }

    fun restoreTitle() {
        setToolbarTitle(baseTitle)
    }

    fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    val playSoundTask = remember(uiState) {
        object : Runnable {
            override fun run() {
                val db = uiState.snrDb
                val freq = (1650 * db * db) / 1000 + 200
                playAcousticTone(freq)
                var delay = MIN_DELAY * (MAX_SNR_DB.toDouble().pow(3) / db.pow(3))
                if (delay > MAX_DELAY) {
                    delay = MAX_DELAY.toDouble()
                }
                handler.postDelayed(this, delay.toLong())
            }
        }
    }

    fun stopAcoustic() {
        handler.removeCallbacks(playSoundTask)
    }

    fun stopPolling() {
        stopAcoustic()
        signalGeneration++
        cancelLoad()
        isUpdating = false
        uiState.clearMeter()
        restoreTitle()
    }

    fun applySignal(signal: Signal) {
        val stopTime = System.currentTimeMillis()
        Log.w(TAG, "request & parsing took: ${stopTime - startTime}ms")
        uiState.apply(signal, MIN_SNR_DB.toDouble())
    }

    fun reload() {
        startTime = System.currentTimeMillis()
        setToolbarTitle("$baseTitle - ${context.getString(R.string.loading)}")
        if (isUpdating) {
            return
        }
        isUpdating = true
        val generation = ++signalGeneration
        cancelLoad()
        loadJob = scope.launch {
            val result = loadSignal(context.applicationContext)
            if (generation != signalGeneration) {
                return@launch
            }
            isUpdating = false
            restoreTitle()
            if (!uiState.enabled) {
                return@launch
            }
            if (!result.success || result.signal == null) {
                uiState.enabled = false
                stopPolling()
                val message = result.errorText?.takeIf { it.isNotEmpty() }
                if (message != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            applySignal(result.signal)
            reload()
        }
    }

    fun startPolling() {
        isUpdating = false
        if (uiState.enabled) {
            reload()
            if (uiState.acousticFeedback) {
                stopAcoustic()
                handler.post(playSoundTask)
            }
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? AppCompatActivity
        activity?.title = baseTitle
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startPolling()
        onDispose {
            stopPolling()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    SignalScreen(
        state = uiState,
        onEnabledChange = { enabled ->
            if (enabled) {
                startPolling()
            } else {
                stopPolling()
            }
        },
        onAcousticChange = { acoustic ->
            if (acoustic) {
                if (uiState.enabled) {
                    stopAcoustic()
                    handler.post(playSoundTask)
                }
            } else {
                stopAcoustic()
            }
        },
        modifier = modifier,
    )
}

private fun playAcousticTone(freqOfTone: Double) {
    val duration = 0.075
    val sampleRate = 44100
    val numSamples = ceil(duration * sampleRate).toInt()
    val sample = DoubleArray(numSamples)
    val generatedSnd = ByteArray(2 * numSamples)
    val audioTrack = try {
        AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            numSamples * 2,
            AudioTrack.MODE_STATIC,
        )
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
    while (head < numSamples) {
        head = audioTrack.playbackHeadPosition
    }
    audioTrack.release()
}
