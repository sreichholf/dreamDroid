package net.reichholf.dreamdroid.widget.behaviour;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.view.ViewCompat;
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
	public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull FloatingActionButton child, @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
		return axes == ViewCompat.SCROLL_AXIS_VERTICAL
				|| super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type);
	}

	@Override
	public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull FloatingActionButton child, @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
		super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type);
		Boolean tag = (Boolean) child.getTag(R.id.fab_scrolling_view_behavior_enabled);
		if(tag == null || child.getTag(R.id.fab_scrolling_view_behavior_enabled).equals(false)) {
			child.hide();
			return;
		}

		if (dyConsumed > 0 && child.getVisibility() == View.VISIBLE) {
			child.hide(new FloatingActionButton.OnVisibilityChangedListener() {
				@Override
				public void onHidden(FloatingActionButton fab) {
					super.onHidden(fab);
					View v = fab;
					v.setVisibility(View.INVISIBLE);
				}
			});
		} else if (dyConsumed < 0 && child.getVisibility() != View.VISIBLE) {
			child.show();
		}
	}
}
