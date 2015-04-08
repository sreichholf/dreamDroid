package net.reichholf.dreamdroid.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ProgressBar;

import net.reichholf.dreamdroid.R;

/**
 * Created by Stephan on 08.04.2015.
 */
public class MaterialProgressBar extends ProgressBar {
	public MaterialProgressBar(Context context) {
		super(context);
		init();
	}

	public MaterialProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public MaterialProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@TargetApi(21)
	public MaterialProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public void init(){
		if(Build.VERSION.SDK_INT < 11)
			return;
		TypedValue typedValue = new TypedValue();
		getContext().getTheme().resolveAttribute(R.attr.colorAccent, typedValue, true);
		setColorFilter(getContext().getResources().getColor(typedValue.resourceId));
	}

	public void setColorFilter(int color) {
		if(Build.VERSION.SDK_INT >= 21) {
			ColorStateList stateList = ColorStateList.valueOf(color);
			setProgressTintList(stateList);
			setSecondaryProgressTintList(stateList);
			setIndeterminateTintList(stateList);
		} else {
			if(getIndeterminateDrawable() != null) {
				getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
			}

			if(getProgressDrawable() != null) {
				getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
			}
		}
	}
}
