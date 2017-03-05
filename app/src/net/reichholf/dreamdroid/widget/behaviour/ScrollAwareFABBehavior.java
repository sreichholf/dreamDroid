package net.reichholf.dreamdroid.widget.behaviour;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;

import net.reichholf.dreamdroid.R;

/**
 * Created by reichi on 04/02/16.
 */
@SuppressWarnings("unused")
public class ScrollAwareFABBehavior extends FloatingActionButton.Behavior {

	public ScrollAwareFABBehavior(Context context, AttributeSet attrs) {
		super();
	}

	@Override
	public boolean onStartNestedScroll(final CoordinatorLayout coordinatorLayout, final FloatingActionButton child,
									   final View directTargetChild, final View target, final int nestedScrollAxes) {
		// Ensure we react to vertical scrolling
		return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL
				|| super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, nestedScrollAxes);
	}

	@Override
	public void onNestedScroll(final CoordinatorLayout coordinatorLayout, final FloatingActionButton child,
							   final View target, final int dxConsumed, final int dyConsumed,
							   final int dxUnconsumed, final int dyUnconsumed) {
		super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
		Boolean tag = (Boolean) child.getTag(R.id.fab_scrolling_view_behavior_enabled);
		if(tag == null || child.getTag(R.id.fab_scrolling_view_behavior_enabled).equals(false)) {
			child.hide();
			return;
		}

		if (dyConsumed > 0 && child.getVisibility() == View.VISIBLE) {
			child.hide(new FloatingActionButton.OnVisibilityChangedListener() {
				@Override
				public void onHidden(FloatingActionButton fab) {
					super.onShown(fab);
					fab.setVisibility(View.INVISIBLE);
				}
			});
		} else if (dyConsumed < 0 && child.getVisibility() != View.VISIBLE) {
			child.show();
		}
	}
}
