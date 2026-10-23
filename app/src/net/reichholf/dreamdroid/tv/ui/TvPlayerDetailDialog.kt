package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.epg.EpgDetailBody
import net.reichholf.dreamdroid.ui.epg.EpgDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailScreen

const val TV_PLAYER_DETAIL_DIALOG_TAG = "tv_player_detail_dialog"
const val TV_PLAYER_DETAIL_BODY_TAG = "tv_player_detail_body"

/** The running programme's details over the player, as a dialog: TV has no sheet to drag. */
@Composable
fun TvPlayerEpgDetailDialog(content: EpgDetailContent, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    TvPlayerDetailDialog(scrollState = scrollState, onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            EpgDetailBody(content = content)
        }
    }
}

/** A recording's details over the player. */
@Composable
fun TvPlayerMovieDetailDialog(content: MovieDetailContent, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    TvPlayerDetailDialog(scrollState = scrollState, onDismiss = onDismiss) {
        MovieDetailScreen(content = content, heightCap = null, scrollState = scrollState)
    }
}

/**
 * The body takes focus so Up/Down scroll it; past its end, Down reaches Close. Back
 * dismisses.
 */
@Composable
private fun TvPlayerDetailDialog(
    scrollState: ScrollState,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit
) {
    val bodyFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { ScrollStep.toPx() }
    LaunchedEffect(Unit) {
        try {
            bodyFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Dialog window not attached yet.
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TV_PLAYER_DETAIL_BODY_TAG)
                    .focusRequester(bodyFocus)
                    .onPreviewKeyEvent { event ->
                        val down = when (event.key) {
                            Key.DirectionDown -> true
                            Key.DirectionUp -> false
                            else -> return@onPreviewKeyEvent false
                        }
                        val canScroll = if (down) {
                            scrollState.canScrollForward
                        } else {
                            scrollState.canScrollBackward
                        }
                        if (canScroll && event.type == KeyEventType.KeyDown) {
                            scope.launch { scrollState.animateScrollBy(if (down) step else -step) }
                        }
                        canScroll
                    }
                    .focusable()
            ) {
                body()
            }
        },
        modifier = Modifier.testTag(TV_PLAYER_DETAIL_DIALOG_TAG)
    )
}

private val ScrollStep = 120.dp
