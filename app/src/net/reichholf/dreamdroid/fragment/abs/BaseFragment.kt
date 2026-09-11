/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.fragment.helper.FragmentHelper
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment
import net.reichholf.dreamdroid.fragment.interfaces.IMutliPaneContent
import net.reichholf.dreamdroid.helpers.Statics

/**
 * @author sre
 */
abstract class BaseFragment :
    Fragment(),
    ActivityCallbackHandler,
    IMutliPaneContent,
    IBaseFragment,
    ActionDialog.DialogActionListener {
    private var mHelper: FragmentHelper? = FragmentHelper()
    protected var mShouldRetainInstance: Boolean = true
    protected var mHasFabMain: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mHelper == null) {
            mHelper = FragmentHelper(this)
        } else {
            mHelper!!.bindToFragment(this)
        }
        mHelper!!.onCreate(savedInstanceState)
        if (mShouldRetainInstance) {
            retainInstance = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setFabEnabled(R.id.fab_main, mHasFabMain)
    }

    protected fun setFabEnabled(id: Int, enabled: Boolean) {
        val fab = getAppCompatActivity()?.findViewById<FloatingActionButton>(id) ?: return
        if (enabled) {
            fab.show()
        } else {
            fab.hide()
            (getAppCompatActivity() as MainActivity).unregisterFab(id)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mHelper!!.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        mHelper!!.onResume()
    }

    override fun onPause() {
        mHelper!!.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mHelper!!.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val mph = mHelper?.getMultiPaneHandler() // TODO how do i reproduce this?
        if (mph == null || !mph.isDrawerOpen) {
            createOptionsMenu(menu, inflater)
        }
    }

    override fun createOptionsMenu(menu: Menu, inflater: MenuInflater) {
    }

    override fun onDrawerOpened() {
    }

    override fun onDrawerClosed() {
    }

    override fun hasHeader(): Boolean {
        return false
    }

    fun getBaseTitle(): String? {
        return mHelper!!.getBaseTitle()
    }

    fun setBaseTitle(baseTitle: String?) {
        mHelper!!.setBaseTitle(baseTitle)
    }

    fun getCurrentTitle(): String? {
        return mHelper!!.getCurrenTtitle()
    }

    fun setCurrentTitle(currentTitle: String?) {
        mHelper!!.setCurrentTitle(currentTitle)
    }

    fun initTitles(title: String?) {
        mHelper!!.setBaseTitle(title)
        mHelper!!.setCurrentTitle(title)
    }

    override fun getMultiPaneHandler(): MultiPaneHandler {
        return mHelper!!.getMultiPaneHandler()!!
    }

    protected fun finish() {
        finish(Statics.RESULT_NONE, null)
    }

    protected fun finish(resultCode: Int) {
        finish(resultCode, null)
    }

    protected fun finish(resultCode: Int, data: Intent?) {
        mHelper!!.finish(resultCode, data)
    }

    protected fun getAppCompatActivity(): AppCompatActivity? {
        return activity as AppCompatActivity?
    }

    protected fun showToast(toastText: String?) {
        val toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG)
        toast.show()
    }

    protected fun showToast(toastText: CharSequence?) {
        val toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG)
        toast.show()
    }

    protected fun registerFab(
        id: Int,
        descriptionId: Int,
        backgroundResId: Int,
        onClickListener: View.OnClickListener?,
    ) {
        val fab = getAppCompatActivity()?.findViewById<FloatingActionButton>(id) ?: return

        fab.show()
        fab.contentDescription = getString(descriptionId)
        fab.setImageResource(backgroundResId)
        fab.setOnClickListener(onClickListener)
        fab.setOnLongClickListener { v ->
            Toast.makeText(getAppCompatActivity(), v.contentDescription, Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
    }
}
