package net.reichholf.dreamdroid.fragment

import android.view.KeyEvent

interface ActivityCallbackHandler {
    fun onDrawerOpened()
    fun onDrawerClosed()
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
}
