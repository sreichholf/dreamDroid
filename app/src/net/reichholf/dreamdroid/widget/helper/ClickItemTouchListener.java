package net.reichholf.dreamdroid.widget.helper;

import android.os.Build;
import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;

abstract class ClickItemTouchListener implements OnItemTouchListener {
	private final GestureDetectorCompat mGestureDetector;

	ClickItemTouchListener(RecyclerView hostView) {
		mGestureDetector = new GestureDetectorCompat(hostView.getContext(),
				new ItemClickGestureListener(hostView));
	}

	private boolean isAttachedToWindow(RecyclerView hostView) {
		if (Build.VERSION.SDK_INT >= 19) {
			return hostView.isAttachedToWindow();
		} else {
			return (hostView.getHandler() != null);
		}
	}

	private boolean hasAdapter(RecyclerView hostView) {
		return (hostView.getAdapter() != null);
	}

	@Override
	public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
		if (!isAttachedToWindow(recyclerView) || !hasAdapter(recyclerView)) {
			return false;
		}

		mGestureDetector.onTouchEvent(event);
		return false;
	}

	@Override
	public void onTouchEvent(RecyclerView recyclerView, MotionEvent event) {
		// We can silently track tap and and long presses by silently
		// intercepting touch events in the host RecyclerView.
	}

	abstract boolean performItemClick(RecyclerView parent, View view, int position, long id);
	abstract boolean performItemLongClick(RecyclerView parent, View view, int position, long id);

	private class ItemClickGestureListener extends SimpleOnGestureListener {
		private final RecyclerView mHostView;
		private View mTargetChild;

		public ItemClickGestureListener(RecyclerView hostView) {
			mHostView = hostView;
		}

		@Override
		public boolean onDown(MotionEvent event) {
			final int x = (int) event.getX();
			final int y = (int) event.getY();

			mTargetChild = mHostView.findChildViewUnder(x, y);
			return (mTargetChild != null);
		}

		@Override
		public void onShowPress(MotionEvent event) {
			if (mTargetChild != null) {
				mTargetChild.setPressed(true);
			}
		}

		@Override
		public boolean onSingleTapUp(MotionEvent event) {
			boolean handled = false;

			if (mTargetChild != null) {
				mTargetChild.setPressed(false);

				final int position = mHostView.getChildAdapterPosition(mTargetChild);
				final long id = mHostView.getAdapter().getItemId(position);
				handled = performItemClick(mHostView, mTargetChild, position, id);

				mTargetChild = null;
			}

			return handled;
		}

		@Override
		public boolean onScroll(MotionEvent event, MotionEvent event2, float v, float v2) {
			if (mTargetChild != null) {
				mTargetChild.setPressed(false);
				mTargetChild = null;

				return true;
			}

			return false;
		}

		@Override
		public void onLongPress(MotionEvent event) {
			if (mTargetChild == null) {
				return;
			}

			final int position = mHostView.getChildAdapterPosition(mTargetChild);
			final long id = mHostView.getAdapter().getItemId(position);
			final boolean handled = performItemLongClick(mHostView, mTargetChild, position, id);

			if (handled) {
				mTargetChild.setPressed(false);
				mTargetChild = null;
			}
		}
	}
}
