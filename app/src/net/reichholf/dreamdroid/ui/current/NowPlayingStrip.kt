package net.reichholf.dreamdroid.ui.current

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage

/** Compact now-playing chrome for the TV & Movies hub (status, not a destination). */
@Composable
fun NowPlayingStrip(
    label: String,
    headline: String,
    progress: Float,
    serviceReference: String,
    serviceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val piconsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))
    val description = "$label. $headline"
    Column(modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    contentDescription = description
                }
                .clickable(onClick = onClick)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (piconsEnabled &&
                        (serviceReference.isNotEmpty() || serviceName.isNotEmpty())
                    ) {
                        PiconImage(
                            reference = serviceReference,
                            name = serviceName,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .width(40.dp)
                                .height(28.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
        }
        // M3 full-width divider (outlineVariant / DividerDefaults) between the
        // strip and the destination NavigationBar — same surface would otherwise
        // blend the two chrome regions.
        HorizontalDivider(modifier = Modifier.testTag(NOW_PLAYING_STRIP_DIVIDER_TAG))
    }
}

const val NOW_PLAYING_STRIP_DIVIDER_TAG = "now_playing_strip_divider"

fun nowPlayingHeadline(
    ready: Boolean,
    serviceName: String,
    eventTitle: String,
    loadingText: String,
    unavailableText: String
): String {
    if (!ready) {
        return loadingText
    }
    return when {
        serviceName.isNotEmpty() && eventTitle.isNotEmpty() ->
            "$serviceName · $eventTitle"

        serviceName.isNotEmpty() -> serviceName

        eventTitle.isNotEmpty() -> eventTitle

        else -> unavailableText
    }
}

/** Live `/web/getcurrent` is Online-only; Offline uses the session word, not last-good. */
fun nowPlayingFallbackText(
    sessionOffline: Boolean,
    offlineText: String,
    unavailableText: String
): String = if (sessionOffline) offlineText else unavailableText

fun eventProgressFraction(event: Event?): Float {
    if (event == null) {
        return 0f
    }
    val duration = event.duration
    val start = event.start
    if (duration.isEmpty() || start.isEmpty() ||
        duration == Python.NONE || start == Python.NONE
    ) {
        return 0f
    }
    return try {
        val max = (duration.toDouble() / 60).toLong().toInt()
        if (max <= 0) {
            0f
        } else {
            val remaining = DateTime.getRemaining(duration, start, event.currentTime)
            val cur = (max - remaining).coerceAtLeast(0)
            (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        }
    } catch (_: Exception) {
        0f
    }
}
