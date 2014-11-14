package com.melnykov.fab;

import android.view.View;
import android.widget.AbsListView;

/**
 * Detects which direction list view was scrolled.
 * <p/>
 * Set {@link ScrollDirectionListener} to get callbacks
 * {@link ScrollDirectionListener#onScrollDown()} or
 * {@link ScrollDirectionListener#onScrollUp()}
 *
 * @author Vilius Kraujutis
 */
public abstract class ScrollDirectionDetector implements AbsListView.OnScrollListener {
    private ScrollDirectionListener mScrollDirectionListener;
    private int mLastScrollY;
    private int mPreviousFirstVisibleItem;
    private AbsListView mListView;
    private int mMinSignificantScroll;

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        mMinSignificantScroll = view.getContext().getResources().getDimensionPixelOffset(R.dimen.fab_min_significant_scroll);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mScrollDirectionListener != null) {
            if(isScrollDown(firstVisibleItem))
                mScrollDirectionListener.onScrollDown();
            else if (isScrollUp(firstVisibleItem))
                mScrollDirectionListener.onScrollUp();
            mPreviousFirstVisibleItem = firstVisibleItem;
        }

    }

    public ScrollDirectionListener getScrollDirectionListener() {
        return mScrollDirectionListener;
    }

    public void setScrollDirectionListener(ScrollDirectionListener mScrollDirectionListener) {
        this.mScrollDirectionListener = mScrollDirectionListener;
    }

    /**
     * @return true if scrolled up or false otherwise
     * @see #isSignificantDelta(int, boolean) which ensures, that events are not fired it there was no scrolling
     */
    private boolean isScrollUp(int firstVisibleItem) {
        boolean scrollUp = firstVisibleItem > mPreviousFirstVisibleItem;
        scrollUp |= isSignificantDelta(firstVisibleItem, true);
        return scrollUp;
    }

    private boolean isScrollDown(int firstVisibleItem) {
        boolean scrollDown = firstVisibleItem < mPreviousFirstVisibleItem;
        scrollDown |= isSignificantDelta(firstVisibleItem, false);
        return scrollDown;
    }

    /**
     * Make sure wrong direction method is not called when stopping scrolling
     * and finger moved a little to opposite direction.
     *
     * @see #isScrollUp(int)
     */
    private boolean isSignificantDelta(int firstVisibleItem, boolean isUp) {
        if(!isSameRow(firstVisibleItem)) {
            return false;
        }
        int newScrollY = getTopItemScrollY();
        boolean isDesiredDirection;
        if(isUp)
            isDesiredDirection = mLastScrollY > newScrollY;
        else
            isDesiredDirection = mLastScrollY < newScrollY;
        boolean isSignificantDelta = Math.abs(mLastScrollY - newScrollY) > mMinSignificantScroll;
        if (isSignificantDelta && isDesiredDirection)
            mLastScrollY = newScrollY;
        return isSignificantDelta && isDesiredDirection;
    }

    /**
     * <code>newScrollY</code> position might not be correct if:
     * <ul>
     * <li><code>firstVisibleItem</code> is different than <code>mPreviousFirstVisibleItem</code></li>
     * <li>list has rows of different height</li>
     * </ul>
     * <p/>
     * It's necessary to track if row did not change, so events
     * {@link ScrollDirectionListener#onScrollUp()} or {@link ScrollDirectionListener#onScrollDown()} could be fired with confidence
     *
     * @see #getTopItemScrollY()
     */
    private boolean isSameRow(int firstVisibleItem) {
        boolean isSame = firstVisibleItem == mPreviousFirstVisibleItem;
        mPreviousFirstVisibleItem = firstVisibleItem;
        if(!isSame) {
            mLastScrollY = getTopItemScrollY();
        }
        return isSame;
    }

    /**
     * Will be incorrect if rows has changed and if list has rows of different heights
     * <p/>
     * So when measuring scroll direction, it's necessary to ignore this value
     * if first visible row is different than previously calculated.
     */
    private int getTopItemScrollY() {
        if (mListView == null || mListView.getChildAt(0) == null) return 0;
        View topChild = mListView.getChildAt(0);
        return topChild.getTop();
    }

    public void setListView(AbsListView listView) {
        mListView = listView;
    }
}