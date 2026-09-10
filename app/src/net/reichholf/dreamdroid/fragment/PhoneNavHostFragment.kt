package net.reichholf.dreamdroid.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.abs.BaseFragment
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.bindPhoneNavHost

/**
 * Phase 2.1c beachhead: hosts Compose [androidx.navigation.compose.NavHost] in the phone
 * detail pane. Device Info is the first leaf route; other destinations still use
 * [net.reichholf.dreamdroid.fragment.helper.NavigationHelper] + Fragment transactions.
 *
 * Activity callbacks and profile HTTP hooks must target the nested leaf, not this wrapper —
 * see [getActiveLeaf].
 */
class PhoneNavHostFragment : BaseFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        mShouldRetainInstance = false
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            bindPhoneNavHost(this@PhoneNavHostFragment)
        }
    }

    /** Nested destination fragment currently shown under the NavHost (if any). */
    fun getActiveLeaf(): Fragment? {
        return childFragmentManager.findFragmentById(R.id.phone_nav_device_info_slot)
            ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.DEVICE_INFO)
    }

    override fun onDrawerOpened() {
        (getActiveLeaf() as? ActivityCallbackHandler)?.onDrawerOpened()
    }

    override fun onDrawerClosed() {
        (getActiveLeaf() as? ActivityCallbackHandler)?.onDrawerClosed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return (getActiveLeaf() as? ActivityCallbackHandler)?.onKeyDown(keyCode, event) ?: false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return (getActiveLeaf() as? ActivityCallbackHandler)?.onKeyUp(keyCode, event) ?: false
    }
}
