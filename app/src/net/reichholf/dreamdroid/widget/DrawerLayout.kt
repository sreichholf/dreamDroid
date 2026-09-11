/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * @author sre
 */
class DrawerLayout : androidx.drawerlayout.widget.DrawerLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    /* We need this hack to avoid "random" exceptions with the DrawerLayouts, nothing else we can currently do about that */
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            false
        } catch (e: ArrayIndexOutOfBoundsException) {
            e.printStackTrace()
            false
        }
    }
}
