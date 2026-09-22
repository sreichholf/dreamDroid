package net.reichholf.dreamdroid.helpers

import android.view.KeyEvent
import net.reichholf.dreamdroid.helpers.enigma2.Volume

/**
 * Hardware volume-key routing when Settings `volume_control` is on.
 * Phone [net.reichholf.dreamdroid.activities.MainActivity] sends `/web/vol?set=`.
 */

fun consumesHardwareVolume(prefEnabled: Boolean, keyCode: Int): Boolean {
    if (!prefEnabled) {
        return false
    }
    return keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
}

fun enigmaVolumeCommand(keyCode: Int): String? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> Volume.CMD_UP
    KeyEvent.KEYCODE_VOLUME_DOWN -> Volume.CMD_DOWN
    else -> null
}

fun enigmaVolumeSetParams(keyCode: Int): List<NameValuePair>? {
    val cmd = enigmaVolumeCommand(keyCode) ?: return null
    return listOf(NameValuePair("set", cmd))
}
