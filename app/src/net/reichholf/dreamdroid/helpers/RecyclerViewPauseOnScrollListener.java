package net.reichholf.dreamdroid.helpers;

import android.support.v7.widget.RecyclerView;

import com.squareup.picasso.Picasso;

/**
 * Created by Stephan on 03.02.2016.
 */
public class RecyclerViewPauseOnScrollListener extends RecyclerView.OnScrollListener {
	private final String mTag;
	private final boolean mPauseOnScroll;
	private final boolean mPauseOnSettling;
	private final RecyclerView.OnScrollListener mExternalListener;

	public RecyclerViewPauseOnScrollListener(String tag, boolean pauseOnScroll, boolean pauseOnSettling) {
		this(tag, pauseOnScroll, pauseOnSettling, null);
	}

	public RecyclerViewPauseOnScrollListener(String tag, boolean pauseOnScroll, boolean pauseOnSettling,
											 RecyclerView.OnScrollListener customListener) {
		mPauseOnScroll = pauseOnScroll;
		mPauseOnSettling = pauseOnSettling;
		mExternalListener = customListener;
		mTag = tag;
	}

	@Override
	public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
		switch (newState) {
			case RecyclerView.SCROLL_STATE_IDLE:
				Picasso.with(recyclerView.getContext()).resumeTag(mTag);
				break;
			case RecyclerView.SCROLL_STATE_DRAGGING:
				if (mPauseOnScroll) {
					Picasso.with(recyclerView.getContext()).pauseTag(mTag);
				}
				break;
			case RecyclerView.SCROLL_STATE_SETTLING:
				if (mPauseOnSettling) {
					Picasso.with(recyclerView.getContext()).pauseTag(mTag);
				}
				break;
		}
		if (mExternalListener != null) {
			mExternalListener.onScrollStateChanged(recyclerView, newState);
		}
	}

	@Override
	public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
		if (mExternalListener != null) {
			mExternalListener.onScrolled(recyclerView, dx, dy);
		}
	}
}
