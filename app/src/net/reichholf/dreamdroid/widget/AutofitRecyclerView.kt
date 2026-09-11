package net.reichholf.dreamdroid.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView

class AutofitRecyclerView : FastScrollRecyclerView {
    private var mColumnWidth = -1
    private var mSpanCount = 4
    private var mMaxSpanCount = DEFAULT_MAX_SPAN_COUNT

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(context, attrs)
    }

    fun setMaxSpanCount(maxSpanCount: Int) {
        mMaxSpanCount = maxSpanCount
    }

    fun setColumnWidth(columnWidth: Int) {
        mColumnWidth = columnWidth
        invalidate()
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val attrsArray = intArrayOf(android.R.attr.columnWidth)
            val array = context.obtainStyledAttributes(attrs, attrsArray)
            mColumnWidth = array.getDimensionPixelSize(0, -1)
            array.recycle()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (mColumnWidth > 0) {
            var newSpanCount = Math.max(1, measuredWidth / mColumnWidth)
            if (mMaxSpanCount > 0) {
                newSpanCount = Math.min(mMaxSpanCount, newSpanCount)
            }
            mSpanCount = newSpanCount
            (layoutManager as GridLayoutManager).spanCount = mSpanCount
        }
    }

    companion object {
        @JvmField
        var DEFAULT_MAX_SPAN_COUNT: Int = -1
    }
}
