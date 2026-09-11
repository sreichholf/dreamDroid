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
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment

class FragmentHelper {
    private var mFragment: Fragment? = null
    @JvmField
    protected var mCurrentTitle: String? = null
    @JvmField
    protected var mBaseTitle: String? = null

    constructor()

    constructor(fragment: Fragment?) {
        mFragment = fragment
    }

    fun bindToFragment(fragment: Fragment?) {
        mFragment = fragment
    }

    fun getAppCompatActivity(): AppCompatActivity? =
        mFragment?.activity as AppCompatActivity?

    fun onCreate(savedInstanceState: Bundle?) {
        mBaseTitle = mFragment!!.getString(R.string.app_name_release)
        mCurrentTitle = mBaseTitle
    }

    fun onActivityCreated(savedInstanceState: Bundle?) {
        getAppCompatActivity()!!.title = mCurrentTitle
        val header = getAppCompatActivity()!!.findViewById<View?>(R.id.content_header) ?: return
        val hasHeader = (mFragment as IBaseFragment).hasHeader()
        header.visibility = if (hasHeader) View.VISIBLE else View.GONE
    }

    fun onResume() {
        getMultiPaneHandler()!!.onFragmentResume(mFragment!!)
    }

    fun onPause() {
        val mph = getMultiPaneHandler()
        mph?.onFragmentPause(mFragment!!)
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE")
    }

    fun getMultiPaneHandler(): MultiPaneHandler? =
        getAppCompatActivity() as MultiPaneHandler?

    fun getBaseTitle(): String? = mBaseTitle

    fun setBaseTitle(baseTitle: String?) {
        mBaseTitle = baseTitle
    }

    /** Keep Java typo for callers. */
    fun getCurrenTtitle(): String? = mCurrentTitle

    fun setCurrentTitle(currentTitle: String?) {
        mCurrentTitle = currentTitle
    }

    fun finish(resultCode: Int, data: Intent?) {
        var walker = mFragment?.parentFragment
        while (walker != null) {
            if (walker is PhoneNavHostFragment) {
                walker.deliverPickResult(resultCode, data)
                return
            }
            walker = walker.parentFragment
        }
        // Nested leaves finish only under PhoneNavHost; remaining hosts close themselves.
        getAppCompatActivity()!!.setResult(resultCode, data)
        getAppCompatActivity()!!.finish()
    }
}
