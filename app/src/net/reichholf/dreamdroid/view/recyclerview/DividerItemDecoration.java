package net.reichholf.dreamdroid.view.recyclerview;

/*
  Created by Stephan on 21.05.2016.
 */
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

public class DividerItemDecoration extends RecyclerView.ItemDecoration {

	@Nullable
	private Drawable mDivider;
	private boolean mShowFirstDivider = false;
	private boolean mShowLastDivider = false;

	@RecyclerView.Orientation int mOrientation = RecyclerView.VERTICAL;

	public DividerItemDecoration(@NonNull Context context, AttributeSet attrs) {
		final TypedArray a = context
				.obtainStyledAttributes(attrs, new int[]{android.R.attr.listDivider});
		mDivider = a.getDrawable(0);
		a.recycle();
	}

	public DividerItemDecoration(@NonNull Context context, AttributeSet attrs, boolean showFirstDivider,
								 boolean showLastDivider) {
		this(context, attrs);
		mShowFirstDivider = showFirstDivider;
		mShowLastDivider = showLastDivider;
	}

	public DividerItemDecoration(@NonNull Context context, int resId) {
		mDivider = ContextCompat.getDrawable(context, resId);
	}

	public DividerItemDecoration(@NonNull Context context, int resId, boolean showFirstDivider,
								 boolean showLastDivider) {
		this(context, resId);
		mShowFirstDivider = showFirstDivider;
		mShowLastDivider = showLastDivider;
	}

	public DividerItemDecoration(Drawable divider) {
		mDivider = divider;
	}

	public DividerItemDecoration(Drawable divider, boolean showFirstDivider,
								 boolean showLastDivider) {
		this(divider);
		mShowFirstDivider = showFirstDivider;
		mShowLastDivider = showLastDivider;
	}

	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
							   @NonNull RecyclerView.State state) {
		super.getItemOffsets(outRect, view, parent, state);
		if (mDivider == null) {
			return;
		}

		int position = parent.getChildAdapterPosition(view);
		if (position == RecyclerView.NO_POSITION || (position == 0 && !mShowFirstDivider)) {
			return;
		}

		if (mOrientation == -1)
			getOrientation(parent);

		if (mOrientation == LinearLayoutManager.VERTICAL) {
			outRect.top = mDivider.getIntrinsicHeight();
			if (mShowLastDivider && position == (state.getItemCount() - 1)) {
				outRect.bottom = outRect.top;
			}
		} else {
			outRect.left = mDivider.getIntrinsicWidth();
			if (mShowLastDivider && position == (state.getItemCount() - 1)) {
				outRect.right = outRect.left;
			}
		}
	}

	@Override
	public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		if (mDivider == null) {
			super.onDrawOver(c, parent, state);
			return;
		}

		// Initialization needed to avoid compiler warning
		int left = 0, right = 0, top = 0, bottom = 0, size;
		int orientation = mOrientation != -1 ? mOrientation : getOrientation(parent);
		int childCount = parent.getChildCount();

		if (orientation == LinearLayoutManager.VERTICAL) {
			size = mDivider.getIntrinsicHeight();
			left = parent.getPaddingLeft();
			right = parent.getWidth() - parent.getPaddingRight();
		} else { //horizontal
			size = mDivider.getIntrinsicWidth();
			top = parent.getPaddingTop();
			bottom = parent.getHeight() - parent.getPaddingBottom();
		}

		for (int i = mShowFirstDivider ? 0 : 1; i < childCount; i++) {
			View child = parent.getChildAt(i);
			RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

			if (orientation == LinearLayoutManager.VERTICAL) {
				top = child.getTop() - params.topMargin - size;
				bottom = top + size;
			} else { //horizontal
				left = child.getLeft() - params.leftMargin;
				right = left + size;
			}
			mDivider.setBounds(left, top, right, bottom);
			mDivider.draw(c);
		}

		// show last divider
		if (mShowLastDivider && childCount > 0) {
			View child = parent.getChildAt(childCount - 1);
			if (parent.getChildAdapterPosition(child) == (state.getItemCount() - 1)) {
				RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
				if (orientation == LinearLayoutManager.VERTICAL) {
					top = child.getBottom() + params.bottomMargin;
					bottom = top + size;
				} else { // horizontal
					left = child.getRight() + params.rightMargin;
					right = left + size;
				}
				mDivider.setBounds(left, top, right, bottom);
				mDivider.draw(c);
			}
		}
	}

	private int getOrientation(@NonNull RecyclerView parent) {
		if (mOrientation == -1) {
			if (parent.getLayoutManager() instanceof LinearLayoutManager) {
				LinearLayoutManager layoutManager = (LinearLayoutManager) parent.getLayoutManager();
				mOrientation = layoutManager.getOrientation();
			} else {
				throw new IllegalStateException(
						"DividerItemDecoration can only be used with a LinearLayoutManager.");
			}
		}
		return mOrientation;
	}
}
