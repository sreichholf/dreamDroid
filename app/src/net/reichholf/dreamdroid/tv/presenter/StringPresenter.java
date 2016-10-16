package net.reichholf.dreamdroid.tv.presenter;

import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by Stephan on 16.10.2016.
 */

public class StringPresenter extends Presenter {
	private static final String TAG = "StringPresenter";

	public ViewHolder onCreateViewHolder(ViewGroup parent) {
		TextView textView = new TextView(parent.getContext());
		textView.setFocusable(true);
		textView.setFocusableInTouchMode(true);
		return new ViewHolder(textView);
	}

	public void onBindViewHolder(ViewHolder viewHolder, Object item) {
		((TextView) viewHolder.view).setText(item.toString());
	}

	public void onUnbindViewHolder(ViewHolder viewHolder) {
		// no op
	}
}
