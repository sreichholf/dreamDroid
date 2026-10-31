package net.reichholf.dreamdroid.ui.video

import android.view.KeyEvent

private val systemKeys =
    setOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_SLEEP,
        KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_SOFT_SLEEP,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_FOCUS,
        KeyEvent.KEYCODE_SEARCH,
        KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_BRIGHTNESS_UP,
        KeyEvent.KEYCODE_BRIGHTNESS_DOWN,
        KeyEvent.KEYCODE_CALL,
        KeyEvent.KEYCODE_ENDCALL,
        KeyEvent.KEYCODE_NOTIFICATION,
        KeyEvent.KEYCODE_SETTINGS,
        KeyEvent.KEYCODE_SYSRQ
    )

/** Keys the system acts on; the player leaves them alone so they work on the first press. */
fun isSystemKey(keyCode: Int): Boolean = keyCode in systemKeys
