package net.reichholf.dreamdroid.helpers.enigma2

import android.view.KeyEvent

fun volumeCommandForKey(keyCode: Int): String? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> Volume.CMD_UP
    KeyEvent.KEYCODE_VOLUME_DOWN -> Volume.CMD_DOWN
    else -> null
}

fun shouldConsumeVolumeKey(keyCode: Int, volumeControlEnabled: Boolean): Boolean =
    volumeControlEnabled && volumeCommandForKey(keyCode) != null
