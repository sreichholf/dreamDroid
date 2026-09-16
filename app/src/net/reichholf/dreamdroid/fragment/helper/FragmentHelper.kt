/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.helper

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment

class FragmentHelper {
    private var fragment: Fragment? = null
    var currentTitle: String? = null
    var baseTitle: String? = null

    constructor()

    constructor(fragment: Fragment?) {
        this.fragment = fragment
    }

    fun bindToFragment(fragment: Fragment?) {
        this.fragment = fragment
    }

    fun getAppCompatActivity(): AppCompatActivity? = fragment?.activity as AppCompatActivity?

    fun onCreate(savedInstanceState: Bundle?) {
        baseTitle = fragment!!.getString(R.string.app_name_release)
        currentTitle = baseTitle
    }

    fun onActivityCreated(savedInstanceState: Bundle?) {
        getAppCompatActivity()!!.title = currentTitle
        val header = getAppCompatActivity()!!.findViewById<View?>(R.id.content_header) ?: return
        val hasHeader = (fragment as IBaseFragment).hasHeader()
        header.visibility = if (hasHeader) View.VISIBLE else View.GONE
    }

    fun onResume() {
        getMultiPaneHandler()!!.onFragmentResume(fragment!!)
    }

    fun onPause() {
        val mph = getMultiPaneHandler()
        mph?.onFragmentPause(fragment!!)
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE")
    }

    fun getMultiPaneHandler(): MultiPaneHandler? = getAppCompatActivity() as MultiPaneHandler?

    /** Keep Java typo for callers. */
    fun getCurrenTtitle(): String? = currentTitle

    fun finish(resultCode: Int, data: Intent?) {
        val handle = (getAppCompatActivity() as? MainActivity)?.phoneNav
        if (handle != null) {
            handle.deliverPickResult(resultCode, data)
            return
        }
        // Remaining hosts (e.g. VideoActivity overlay) close themselves.
        getAppCompatActivity()!!.setResult(resultCode, data)
        getAppCompatActivity()!!.finish()
    }
}
