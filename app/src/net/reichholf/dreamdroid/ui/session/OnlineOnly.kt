package net.reichholf.dreamdroid.ui.session

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/** Material 3 disabled opacity. Keep clicks; do not use `enabled = false`. */
const val ONLINE_ONLY_DISABLED_ALPHA = 0.38f

fun Modifier.onlineOnlyLook(blocked: Boolean): Modifier =
    if (blocked) alpha(ONLINE_ONLY_DISABLED_ALPHA) else this
