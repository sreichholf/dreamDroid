package net.reichholf.dreamdroid.ui.video

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

/**
 * The last seconds before the sleep timer closes the player. [onExtend] restarts it with
 * [SLEEP_TIMER_EXTEND_MINUTES]; [onCancel] turns it off.
 */
@Composable
fun SleepTimerCountdown(
    closing: SleepTimer.Closing,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Focus extend so a remote's OK keeps watching without opening the overlay.
    val extendFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { extendFocus.requestFocus() }
    Card(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sleep_timer_closing, closing.secondsLeft),
                modifier = Modifier.padding(end = 8.dp)
            )
            TextButton(onClick = onExtend, modifier = Modifier.focusRequester(extendFocus)) {
                Text(stringResource(R.string.sleep_timer_extend, SLEEP_TIMER_EXTEND_MINUTES))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
