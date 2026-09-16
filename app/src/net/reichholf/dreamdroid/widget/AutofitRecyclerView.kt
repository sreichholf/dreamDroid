package net.reichholf.dreamdroid.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AutofitRecyclerView : RecyclerView {
    private var columnWidthPx = -1
    private var spanCount = 4
    private var spanCountLimit = DEFAULT_MAX_SPAN_COUNT

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context, attrs)
    }

    fun setMaxSpanCount(maxSpanCount: Int) {
        spanCountLimit = maxSpanCount
    }

    fun setColumnWidth(columnWidth: Int) {
        columnWidthPx = columnWidth
        invalidate()
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val attrsArray = intArrayOf(android.R.attr.columnWidth)
            val array = context.obtainStyledAttributes(attrs, attrsArray)
            columnWidthPx = array.getDimensionPixelSize(0, -1)
            array.recycle()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (columnWidthPx > 0) {
            var newSpanCount = Math.max(1, measuredWidth / columnWidthPx)
            if (spanCountLimit > 0) {
                newSpanCount = Math.min(spanCountLimit, newSpanCount)
            }
            spanCount = newSpanCount
            (layoutManager as GridLayoutManager).spanCount = spanCount
        }
    }

    companion object {
        const val DEFAULT_MAX_SPAN_COUNT: Int = -1
    }
}
