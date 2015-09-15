package net.reichholf.dreamdroid.view;

/**
 * Created by Stephan on 21.03.2015.
 * Based on  https://github.com/JJdeGroot/AutoGridView (improved, though)
 */


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.GridView;

public class AutoGridView extends GridView {

    private static final String TAG = "AutoGridView";
    private int mPreviousFirstVisible;
    private int mNumColumns = -1;

    public AutoGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public AutoGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoGridView(Context context) {
        super(context);
    }

    private void updateColumns() {
        mNumColumns = getNumColumns();
    }

    @Override
    public void setNumColumns(int numColumns) {
        mNumColumns = numColumns;
        super.setNumColumns(numColumns);

        Log.d(TAG, "setSelection --> " + mPreviousFirstVisible);
        setSelection(mPreviousFirstVisible);
    }

    @Override
    protected void onLayout(boolean changed, int leftPos, int topPos, int rightPos, int bottomPos) {
        super.onLayout(changed, leftPos, topPos, rightPos, bottomPos);
        setHeights();
    }

    @Override
    protected void onScrollChanged(int newHorizontal, int newVertical, int oldHorizontal, int oldVertical) {
        // Check if the first visible position has changed due to this scroll
        int firstVisible = getFirstVisiblePosition();
        if (mPreviousFirstVisible != firstVisible) {
            // Update position, and update heights
            mPreviousFirstVisible = firstVisible;
            setHeights();
        }
        super.onScrollChanged(newHorizontal, newVertical, oldHorizontal, oldVertical);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateColumns();
    }

    /**
     * Sets the height of each view in a row equal to the height of the tallest view in this row.
     */
    private void setHeights() {
        if (getAdapter() != null && mNumColumns > 1) {
            for (int i = 0; i < getChildCount(); i += mNumColumns) {
                // Determine the maximum height for this row
                int maxHeight = 0;
                for (int j = i; j < i + mNumColumns; j++) {
                    View view = getChildAt(j);
                    if (view != null && view.getHeight() > maxHeight) {
                        maxHeight = view.getHeight();
                    }
                }

                // Set max height for each element in this row
                if (maxHeight > 0) {
                    for (int j = i; j < i + mNumColumns; j++) {
                        View view = getChildAt(j);
                        if (view != null && view.getHeight() != maxHeight) {
                            view.setMinimumHeight(maxHeight);
                        }
                    }
                }
            }
        }
    }
}
