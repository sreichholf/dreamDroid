package net.reichholf.dreamdroid.activities.abs

interface MultiPaneHandler {
    val isMultiPane: Boolean
    val isDrawerOpen: Boolean
    fun finish()
}
