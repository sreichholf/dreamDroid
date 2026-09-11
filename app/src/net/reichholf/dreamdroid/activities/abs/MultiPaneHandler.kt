package net.reichholf.dreamdroid.activities.abs

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment

interface MultiPaneHandler {
    fun showDetails(fragment: Fragment)
    fun showDetails(fragment: Fragment, addToBackStack: Boolean)
    fun onFragmentResume(fragment: Fragment)
    fun onFragmentPause(fragment: Fragment)
    fun showDialogFragment(
        fragmentClass: Class<out DialogFragment>,
        args: Bundle?,
        tag: String,
    )
    fun showDialogFragment(fragment: DialogFragment, tag: String)
    val isMultiPane: Boolean
    val isDrawerOpen: Boolean
    fun finish()
}
