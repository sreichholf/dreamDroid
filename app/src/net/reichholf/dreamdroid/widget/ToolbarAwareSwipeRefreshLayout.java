package net.reichholf.dreamdroid.widget;

import android.app.Activity;
import android.content.Context;
import android.support.design.widget.AppBarLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

import net.reichholf.dreamdroid.R;

/**
 * http://stackoverflow.com/questions/30833589/scrolling-down-triggers-refresh-instead-of-revealing-the-toolbar
 */
public class ToolbarAwareSwipeRefreshLayout  extends SwipeRefreshLayout implements AppBarLayout.OnOffsetChangedListener {
	private AppBarLayout appBarLayout;

	public ToolbarAwareSwipeRefreshLayout(Context context) {
		super(context);
	}

	public ToolbarAwareSwipeRefreshLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (getContext() instanceof Activity) {
			appBarLayout = (AppBarLayout) ((Activity) getContext()).findViewById(R.id.appbar);
			appBarLayout.addOnOffsetChangedListener(this);
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		appBarLayout.removeOnOffsetChangedListener(this);
		appBarLayout = null;
		super.onDetachedFromWindow();
	}

	@Override
	public void onOffsetChanged(AppBarLayout appBarLayout, int i) {
		this.setEnabled(i == 0);
	}
}

