package net.reichholf.dreamdroid.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

import java.util.ArrayList;

/**
 * Created by Stephan on 11.03.14.
 */
public class EnhancedHorizontalScrollView extends HorizontalScrollView {
	protected ArrayList<OnScrollChangedListener> mScrollChangedListeners;

	public EnhancedHorizontalScrollView(Context context) {
		super(context);
		init();
	}

	public EnhancedHorizontalScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public EnhancedHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		mScrollChangedListeners = new ArrayList<>();
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		for (OnScrollChangedListener listener : mScrollChangedListeners) {
			listener.onScrollChanged(l, t);
		}
	}

	public void addScrollChangedListener(OnScrollChangedListener listener) {
		mScrollChangedListeners.add(listener);
	}

	public void removeScrollChangedListener(OnScrollChangedListener listener) {
		mScrollChangedListeners.remove(listener);
	}

	public interface OnScrollChangedListener {
		void onScrollChanged(int x, int y);
	}
}
