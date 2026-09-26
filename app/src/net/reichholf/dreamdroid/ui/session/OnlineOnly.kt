package net.reichholf.dreamdroid.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import net.reichholf.dreamdroid.R

/** Material 3 disabled opacity. Keep clicks; do not use `enabled = false`. */
const val ONLINE_ONLY_DISABLED_ALPHA = 0.38f

/**
 * Grey an online-only control without swallowing the click. TalkBack hears
 * [R.string.session_needs_receiver] as the state and
 * [R.string.session_needs_receiver_long] as the click label.
 */
@Composable
fun Modifier.onlineOnlyLook(blocked: Boolean): Modifier {
    if (!blocked) {
        return this
    }
    val needsReceiver = stringResource(R.string.session_needs_receiver)
    val explain = stringResource(R.string.session_needs_receiver_long)
    return this
        .alpha(ONLINE_ONLY_DISABLED_ALPHA)
        .semantics {
            stateDescription = needsReceiver
            onClick(label = explain, action = null)
        }
}
