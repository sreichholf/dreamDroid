package net.reichholf.dreamdroid.ui.remote

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Remote
import java.util.HashMap

/**
 * Widget RemoteViews button → Enigma2 RCU key map.
 * Moved off [net.reichholf.dreamdroid.fragment.VirtualRemoteFragment] in Phase 2.7d.
 */
object VirtualRemoteButtons {
    @JvmStatic
    fun getRemoteButtons(isPlayButtonPlayPause: Boolean): Array<Array<Int>> {
        val buttonMap = HashMap<Int, Int>()
        buttonMap[R.id.ButtonPower] = Remote.KEY_POWER
        buttonMap[R.id.ButtonExit] = Remote.KEY_EXIT
        buttonMap[R.id.ButtonVolP] = Remote.KEY_VOLP
        buttonMap[R.id.ButtonVolM] = Remote.KEY_VOLM
        buttonMap[R.id.ButtonMute] = Remote.KEY_MUTE
        buttonMap[R.id.ButtonBouP] = Remote.KEY_BOUP
        buttonMap[R.id.ButtonBouM] = Remote.KEY_BOUM
        buttonMap[R.id.ButtonUp] = Remote.KEY_UP
        buttonMap[R.id.ButtonDown] = Remote.KEY_DOWN
        buttonMap[R.id.ButtonLeft] = Remote.KEY_LEFT
        buttonMap[R.id.ButtonRight] = Remote.KEY_RIGHT
        buttonMap[R.id.ButtonOk] = Remote.KEY_OK
        buttonMap[R.id.ButtonInfo] = Remote.KEY_INFO
        buttonMap[R.id.ButtonMenu] = Remote.KEY_MENU
        buttonMap[R.id.ButtonHelp] = Remote.KEY_HELP
        buttonMap[R.id.ButtonPvr] = Remote.KEY_PVR
        buttonMap[R.id.ButtonRed] = Remote.KEY_RED
        buttonMap[R.id.ButtonGreen] = Remote.KEY_GREEN
        buttonMap[R.id.ButtonYellow] = Remote.KEY_YELLOW
        buttonMap[R.id.ButtonBlue] = Remote.KEY_BLUE
        buttonMap[R.id.ButtonRwd] = Remote.KEY_REWIND
        if (isPlayButtonPlayPause) {
            buttonMap[R.id.ButtonPlayPause] = Remote.KEY_PLAYPAUSE
        } else {
            buttonMap[R.id.ButtonPlay] = Remote.KEY_PLAY
        }
        buttonMap[R.id.ButtonStop] = Remote.KEY_STOP
        buttonMap[R.id.ButtonFwd] = Remote.KEY_FORWARD
        buttonMap[R.id.ButtonRec] = Remote.KEY_RECORD
        buttonMap[R.id.ButtonAudio] = Remote.KEY_AUDIO
        buttonMap[R.id.Button1] = Remote.KEY_1
        buttonMap[R.id.Button2] = Remote.KEY_2
        buttonMap[R.id.Button3] = Remote.KEY_3
        buttonMap[R.id.Button4] = Remote.KEY_4
        buttonMap[R.id.Button5] = Remote.KEY_5
        buttonMap[R.id.Button6] = Remote.KEY_6
        buttonMap[R.id.Button7] = Remote.KEY_7
        buttonMap[R.id.Button8] = Remote.KEY_8
        buttonMap[R.id.Button9] = Remote.KEY_9
        buttonMap[R.id.Button0] = Remote.KEY_0
        buttonMap[R.id.ButtonLeftArrow] = Remote.KEY_PREV
        buttonMap[R.id.ButtonRightArrow] = Remote.KEY_NEXT
        buttonMap[R.id.ButtonTv] = Remote.KEY_TV
        buttonMap[R.id.ButtonRadio] = Remote.KEY_RADIO
        buttonMap[R.id.ButtonText] = Remote.KEY_TEXT

        return buttonMap.entries.map { entry ->
            arrayOf(entry.key, entry.value)
        }.toTypedArray()
    }
}
