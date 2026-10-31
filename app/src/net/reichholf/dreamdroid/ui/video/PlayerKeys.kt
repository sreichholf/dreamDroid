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

/** What a media key asks the player to do. */
enum class PlayerMediaKey {
    Play,
    Pause,
    TogglePlayPause,

    /** Closes the player, on phones (headset, car) as on TV remotes. */
    Stop,

    /** A held toggle or stop key's auto-repeat: consumed, does nothing. */
    Repeat,
    None
}

fun playerMediaKey(keyCode: Int, repeatCount: Int): PlayerMediaKey {
    val key = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerMediaKey.Play

        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerMediaKey.Pause

        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> PlayerMediaKey.TogglePlayPause

        KeyEvent.KEYCODE_MEDIA_STOP -> PlayerMediaKey.Stop

        else -> PlayerMediaKey.None
    }
    val toggles = key == PlayerMediaKey.TogglePlayPause || key == PlayerMediaKey.Stop
    return if (toggles && repeatCount > 0) PlayerMediaKey.Repeat else key
}
