package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.graphics.Rect;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * Created by Stephan on 16.11.2014.
 */
public class CheckableImageButton extends ImageButton implements Checkable {
	private boolean mChecked;

	private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

	public CheckableImageButton(Context context) {
		super(context);
		init();
	}

	public CheckableImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public CheckableImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public CheckableImageButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public void init(){
		setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				CheckableImageButton.this.onLongClick(view, Gravity.BOTTOM);
				return true;
			}
		});
	}

	public void onLongClick(View view){
		onLongClick(view, Gravity.TOP);
	}

	public void onLongClick(View view, int primaryAlign){
		int secondaryAlign = primaryAlign == Gravity.TOP ? Gravity.BOTTOM : Gravity.TOP;


		CharSequence desc = view.getContentDescription();
		if (desc == null) {
			// Don't show the cheat sheet for items that already show text.
			return;
		}

		final int[] screenPos = new int[2];
		final Rect displayFrame = new Rect();
		getLocationOnScreen(screenPos);
		getWindowVisibleDisplayFrame(displayFrame);

		final Context context = getContext();
		final int width = getWidth();
		final int height = getHeight();
		final int midy = screenPos[1] + height / 2;
		int referenceX = screenPos[0] + width / 2;
		if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
			final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
			referenceX = screenWidth - referenceX; // mirror
		}
		Toast cheatSheet = Toast.makeText(context, view.getContentDescription(), Toast.LENGTH_SHORT);
		if (midy < displayFrame.height()) {
			// Show along the top; follow action buttons
			cheatSheet.setGravity(primaryAlign| GravityCompat.END, referenceX, height);
		} else {
			// Show along the bottom center
			cheatSheet.setGravity(secondaryAlign| Gravity.CENTER_HORIZONTAL, 0, height);
		}
		cheatSheet.show();
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
