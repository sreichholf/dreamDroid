package net.reichholf.dreamdroid.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.view.helper.TintHelper;

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
		TintHelper.setTint(this, TintHelper.getColorFromAttr(getContext(), R.attr.colorAccent));
	}
}
