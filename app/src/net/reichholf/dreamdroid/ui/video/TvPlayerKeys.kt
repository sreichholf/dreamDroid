package net.reichholf.dreamdroid.ui.video

import android.view.KeyEvent

/** What a remote key does in the TV player before focus navigation gets it. */
enum class TvPlayerKey {
    NextChannel,
    PreviousChannel,
    ChannelList,

    /** Not a player shortcut: focus navigation or showing the infobar decides. */
    None
}

/**
 * Live TV on bare video: Left / CH- previous channel, Right / CH+ next, Up or Down the channel
 * list. CH± zap with the overlay open too, since they never move focus. Recordings keep the
 * D-pad for seeking.
 */
fun tvPlayerKey(keyCode: Int, overlayVisible: Boolean, live: Boolean): TvPlayerKey {
    if (!live) {
        return TvPlayerKey.None
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP -> TvPlayerKey.NextChannel

        KeyEvent.KEYCODE_CHANNEL_DOWN -> TvPlayerKey.PreviousChannel

        KeyEvent.KEYCODE_DPAD_RIGHT ->
            if (overlayVisible) TvPlayerKey.None else TvPlayerKey.NextChannel

        KeyEvent.KEYCODE_DPAD_LEFT ->
            if (overlayVisible) TvPlayerKey.None else TvPlayerKey.PreviousChannel

        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
            if (overlayVisible) TvPlayerKey.None else TvPlayerKey.ChannelList

        else -> TvPlayerKey.None
    }
}
