package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.ImageButton;
import android.widget.ImageView;

/**
 * Created by Stephan on 16.11.2014.
 */
public class CheckableImageButton extends ImageButton implements Checkable {
	private boolean mChecked;

	private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

	public CheckableImageButton(Context context) {
		super(context);
	}

	public CheckableImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CheckableImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public CheckableImageButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	public int[] onCreateDrawableState(final int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked())
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		return drawableState;
	}

	@Override
	public void toggle() {
		setChecked(!mChecked);
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	@Override
	public void setChecked(final boolean checked) {
		if (mChecked == checked)
			return;
		mChecked = checked;
		refreshDrawableState();
	}
}
