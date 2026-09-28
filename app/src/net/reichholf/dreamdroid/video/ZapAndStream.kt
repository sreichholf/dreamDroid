package net.reichholf.dreamdroid.video

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.ui.nav.ShellMessages

/**
 * Single-tuner boxes can stream a service only while they are tuned to that
 * transponder. [Profile.zapAndStream] makes live playback zap first. Movie
 * playback does not use this path.
 */
object ZapAndStream {
    fun required(profile: Profile): Boolean = profile.zapAndStream
}

internal fun zapThenStreamFailureText(
    stateText: String?,
    resolvedError: String?,
    fallback: String
): String {
    val box = stateText?.trim().orEmpty()
    if (box.isNotEmpty()) {
        return box
    }
    val resolved = resolvedError?.trim().orEmpty()
    if (resolved.isNotEmpty()) {
        return resolved
    }
    return fallback
}

/**
 * Starts [play] immediately, or after `/web/zap` when the current profile has
 * zap-and-stream enabled. A failed zap does not start playback.
 */
fun LifecycleOwner.startLiveServiceStream(
    context: Context,
    serviceRef: String,
    play: () -> Unit
): Job? {
    if (!ZapAndStream.required(ProfileRepository.get().requireCurrent())) {
        play()
        return null
    }
    if (serviceRef.isEmpty()) {
        ShellMessages.post(context.getString(R.string.get_content_error))
        return null
    }
    return lifecycleScope.launch {
        val (result, error) = EnigmaClient().zap(listOf(NameValuePair("sRef", serviceRef)))
        if (result != null && error == null) {
            play()
        } else {
            toastZapFailure(context, result, error)
        }
    }
}

private fun toastZapFailure(context: Context, result: SimpleResult?, error: EnigmaHttpError?) {
    val message = zapThenStreamFailureText(
        result?.stateText,
        error?.resolve(context),
        context.getString(R.string.get_content_error)
    )
    ShellMessages.post(message)
}
