/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * @author sre
 * 
 */
public class DrawerLayout extends androidx.drawerlayout.widget.DrawerLayout {

	/**
	 * @param context
	 */
	public DrawerLayout(Context context) {
		super(context);
	}

	/**
	 * @param context
	 * @param attrs
	 */
	public DrawerLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public DrawerLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	/* We need this hack to avoid "random" exceptions with the DrawerLayouts, nothing else we can currently do about that
	 * (non-Javadoc)
	 * @see android.support.v4.widget.DrawerLayout#onInterceptTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		try {
			return super.onInterceptTouchEvent(ev);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			return false;
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
			return false;
		}
	}
}
