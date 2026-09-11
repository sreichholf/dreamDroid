package net.reichholf.dreamdroid.fragment.interfaces

import android.view.Menu
import android.view.MenuInflater
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler

interface IMutliPaneContent {
    fun getMultiPaneHandler(): MultiPaneHandler
    fun createOptionsMenu(menu: Menu, inflater: MenuInflater)
}
