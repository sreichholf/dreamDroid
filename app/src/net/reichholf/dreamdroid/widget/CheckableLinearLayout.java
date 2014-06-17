package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

/**
 * Created by Stephan on 02.03.14.
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable {
	private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
	private boolean mIsChecked;

	public CheckableLinearLayout(Context context) {
		super(context);
	}

	public CheckableLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CheckableLinearLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked()) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}

	@Override
	public boolean isChecked() {
		return mIsChecked;
	}

	@Override
	public void setChecked(boolean checked) {
		mIsChecked = checked;
		refreshDrawableState();
	}

	@Override
	public void toggle() {
		mIsChecked = !mIsChecked;
		refreshDrawableState();
	}
}
