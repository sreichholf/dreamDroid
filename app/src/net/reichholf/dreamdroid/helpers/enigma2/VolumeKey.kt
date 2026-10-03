package net.reichholf.dreamdroid.helpers.enigma2

import android.view.KeyEvent
import net.reichholf.dreamdroid.enigma.VolumeCommand

fun volumeCommandForKey(keyCode: Int): VolumeCommand? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> VolumeCommand.Up
    KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeCommand.Down
    else -> null
}

fun shouldConsumeVolumeKey(keyCode: Int, volumeControlEnabled: Boolean): Boolean =
    volumeControlEnabled && volumeCommandForKey(keyCode) != null
