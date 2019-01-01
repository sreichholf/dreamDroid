package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.recyclerview.widget.GridLayoutManager;
import android.util.AttributeSet;

public class AutofitRecyclerView extends com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView {
	public static int DEFAULT_MAX_SPAN_COUNT = -1;
	private int mColumnWidth = -1;
	private int mSpanCount = 4;
	private int mMaxSpanCount = DEFAULT_MAX_SPAN_COUNT;

	public AutofitRecyclerView(Context context) {
		super(context);
		init(context, null);
	}

	public AutofitRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public AutofitRecyclerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	public void setMaxSpanCount(int maxSpanCount) {
		mMaxSpanCount = maxSpanCount;
	}

	private void init(Context context, AttributeSet attrs) {
		if (attrs != null) {
			int[] attrsArray = {
					android.R.attr.columnWidth
			};
			TypedArray array = context.obtainStyledAttributes(attrs, attrsArray);
			mColumnWidth = array.getDimensionPixelSize(0, -1);
			array.recycle();
		}
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		super.onMeasure(widthSpec, heightSpec);
		if (mColumnWidth > 0) {
			int newSpanCount = Math.max(1, getMeasuredWidth() / mColumnWidth);
			if(mMaxSpanCount > 0)
				newSpanCount = Math.min(mMaxSpanCount, newSpanCount);
			mSpanCount = newSpanCount;
			((GridLayoutManager)getLayoutManager()).setSpanCount(mSpanCount);
		}
	}
}
