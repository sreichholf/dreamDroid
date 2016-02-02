package net.reichholf.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;

/**
 * Android Google+ like floating action button which reacts on the attached list view scrolling events.
 *
 * @author Oleksandr Melnykov
 */
public class FloatingActionButton extends android.support.design.widget.FloatingActionButton {
	private static final int TRANSLATE_DURATION_MILLIS = 200;
	private FabOnScrollListener mOnScrollListener;
	private FabRecyclerOnViewScrollListener mRecyclerViewOnScrollListener;

	public boolean isTopAligned() {
		return mTopAligned;
	}

	protected AbsListView mListView;
	protected RecyclerView mRecyclerView;

	private boolean mVisible;
	private boolean mTopAligned;

	public FloatingActionButton(Context context) {
		this(context, null);
	}

	public FloatingActionButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public FloatingActionButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		mVisible = true;
		mTopAligned = false;
	}

	/**
	 * @deprecated to be removed in next release.
	 * Now {@link net.reichholf.widget.ScrollDirectionDetector} is used to detect scrolling direction.
	 */
	@Deprecated
	protected int getListViewScrollY() {
		View topChild = mListView.getChildAt(0);
		return topChild == null ? 0 : mListView.getFirstVisiblePosition() * topChild.getHeight() -
				topChild.getTop();
	}

	private int getMarginBottom() {
		int marginBottom = 0;
		final ViewGroup.LayoutParams layoutParams = getLayoutParams();
		if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
			marginBottom = ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
		}
		return marginBottom;
	}

	private int getMarginTop() {
		int marginTop = 0;
		final ViewGroup.LayoutParams layoutParams = getLayoutParams();
		if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
			marginTop = ((ViewGroup.MarginLayoutParams) layoutParams).topMargin;
		}
		return marginTop;
	}

	protected AbsListView.OnScrollListener getOnScrollListener() {
		return mOnScrollListener;
	}

	protected RecyclerView.OnScrollListener getRecyclerViewOnScrollListener() {
		return mRecyclerViewOnScrollListener;
	}

	public void show() {
		show(true);
	}

	public void hide() {
		hide(true);
	}

	public void show(boolean animate) {
		toggle(true, animate, false);
	}

	public void hide(boolean animate) {
		toggle(false, animate, false);
	}

	private void toggle(final boolean visible, final boolean animate, boolean force) {
		if (mVisible != visible || force) {
			mVisible = visible;
			int height = getHeight();
			if (height == 0 && !force) {
				ViewTreeObserver vto = getViewTreeObserver();
				if (vto.isAlive()) {
					vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
						@Override
						public boolean onPreDraw() {
							ViewTreeObserver currentVto = getViewTreeObserver();
							if (currentVto.isAlive()) {
								currentVto.removeOnPreDrawListener(this);
							}
							toggle(visible, animate, true);
							return true;
						}
					});
					return;
				}
			}

			int translationY = visible ? 0 : height + getMarginBottom();
			int from = visible ? height : 0;
			if (mTopAligned) {
				translationY = visible ? 0 : -(2 * height + getMarginTop());
				from = visible ? -(2 * height) : 0;
			}

			if (animate) {
				animate().setDuration(TRANSLATE_DURATION_MILLIS)
						.translationY(translationY)
						.start();
			}
/*
			ObjectAnimator animator = ObjectAnimator.ofFloat(this,
                    "translationY", from, translationY);
            animator.setInterpolator(mInterpolator);
            if (animate) {
                animator.setDuration(TRANSLATE_DURATION_MILLIS).start();
            } else {
                animator.setDuration(0).start();
            }
*/
		}
	}

	/**
	 * If need to use custom {@link android.widget.AbsListView.OnScrollListener},
	 * pass it to {@link #attachToListView(android.widget.AbsListView, net.reichholf.widget.FloatingActionButton.FabOnScrollListener, boolean inverted)}
	 */
	public void attachToListView(@NonNull AbsListView listView) {
		attachToListView(listView, new FabOnScrollListener(), false);
	}

	public void attachToListView(@NonNull AbsListView listView, boolean isTopAligned) {
		attachToListView(listView, new FabOnScrollListener(), isTopAligned);
	}

	/**
	 * If need to use custom {@link android.widget.AbsListView.OnScrollListener},
	 * pass it to {@link #attachToListView(android.widget.AbsListView, net.reichholf.widget.FloatingActionButton.FabOnScrollListener, boolean inverted)}
	 */
	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, boolean topAligned) {
		attachToRecyclerView(recyclerView, new FabRecyclerOnViewScrollListener(), topAligned);
	}

	public void attachToListView(@NonNull AbsListView listView, @NonNull FabOnScrollListener onScrollListener) {
		attachToListView(listView, onScrollListener, false);
	}

	public void attachToListView(@NonNull AbsListView listView, @NonNull FabOnScrollListener onScrollListener, boolean topAligned) {
		mListView = listView;
		mOnScrollListener = onScrollListener;
		mTopAligned = topAligned;

		onScrollListener.setFloatingActionButton(this);
		onScrollListener.setListView(listView);
		mListView.setOnScrollListener(onScrollListener);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, @NonNull FabRecyclerOnViewScrollListener onScrollListener) {
		attachToRecyclerView(recyclerView, onScrollListener, false);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, @NonNull FabRecyclerOnViewScrollListener onScrollListener, boolean topAligned) {
		mRecyclerView = recyclerView;
		mRecyclerViewOnScrollListener = onScrollListener;
		mTopAligned = topAligned;
		onScrollListener.setFloatingActionButton(this);
		onScrollListener.setRecyclerView(recyclerView);
		mRecyclerView.setOnScrollListener(onScrollListener);
	}

	/**
	 * Shows/hides the FAB when the attached {@link android.widget.AbsListView} scrolling events occur.
	 * Extend this class and override {@link net.reichholf.widget.FloatingActionButton.FabOnScrollListener#onScrollDown()}/{@link net.reichholf.widget.FloatingActionButton.FabOnScrollListener#onScrollUp()}
	 * if you need custom code to be executed on these events.
	 */
	public static class FabOnScrollListener extends ScrollDirectionDetector implements ScrollDirectionListener {
		private FloatingActionButton mFloatingActionButton;

		public FabOnScrollListener() {
			setScrollDirectionListener(this);
		}

		private void setFloatingActionButton(@NonNull FloatingActionButton floatingActionButton) {
			mFloatingActionButton = floatingActionButton;
		}

		/**
		 * Called when the attached {@link android.widget.AbsListView} is scrolled down.
		 * <br />
		 * <br />
		 * <i>Derived classes should call the super class's implementation of this method.
		 * If they do not, the FAB will not react to AbsListView's scrolling events.</i>
		 */
		@Override
		public void onScrollDown() {
			if (mFloatingActionButton.isTopAligned())
				mFloatingActionButton.hide();
			else
				mFloatingActionButton.show();
		}

		/**
		 * Called when the attached {@link android.widget.AbsListView} is scrolled up.
		 * <br />
		 * <br />
		 * <i>Derived classes should call the super class's implementation of this method.
		 * If they do not, the FAB will not react to AbsListView's scrolling events.</i>
		 */
		@Override
		public void onScrollUp() {
			if (mFloatingActionButton.isTopAligned())
				mFloatingActionButton.show();
			else
				mFloatingActionButton.hide();
		}
	}

	/**
	 * Shows/hides the FAB when the attached {@link RecyclerView} scrolling events occur.
	 * Extend this class and override {@link net.reichholf.widget.FloatingActionButton.FabOnScrollListener#onScrollDown()}/{@link net.reichholf.widget.FloatingActionButton.FabOnScrollListener#onScrollUp()}
	 * if you need custom code to be executed on these events.
	 */
	public static class FabRecyclerOnViewScrollListener extends ScrollDirectionRecyclerViewDetector implements ScrollDirectionListener {
		private FloatingActionButton mFloatingActionButton;

		public FabRecyclerOnViewScrollListener() {
			setScrollDirectionListener(this);
		}

		private void setFloatingActionButton(@NonNull FloatingActionButton floatingActionButton) {
			mFloatingActionButton = floatingActionButton;
		}

		/**
		 * Called when the attached {@link RecyclerView} is scrolled down.
		 * <br />
		 * <br />
		 * <i>Derived classes should call the super class's implementation of this method.
		 * If they do not, the FAB will not react to RecyclerView's scrolling events.</i>
		 */
		@Override
		public void onScrollDown() {
			if (mFloatingActionButton.isTopAligned())
				mFloatingActionButton.hide();
			else
				mFloatingActionButton.show();
		}

		/**
		 * Called when the attached {@link RecyclerView} is scrolled up.
		 * <br />
		 * <br />
		 * <i>Derived classes should call the super class's implementation of this method.
		 * If they do not, the FAB will not react to RecyclerView's scrolling events.</i>
		 */
		@Override
		public void onScrollUp() {
			if (mFloatingActionButton.isTopAligned())
				mFloatingActionButton.show();
			else
				mFloatingActionButton.hide();
		}
	}
}
