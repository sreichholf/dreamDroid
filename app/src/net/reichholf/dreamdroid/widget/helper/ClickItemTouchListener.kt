package net.reichholf.dreamdroid.widget.helper

import android.os.Build
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener

abstract class ClickItemTouchListener(hostView: RecyclerView) : OnItemTouchListener {
    private val mGestureDetector: GestureDetectorCompat =
        GestureDetectorCompat(hostView.context, ItemClickGestureListener(hostView))

    private fun isAttachedToWindow(hostView: RecyclerView): Boolean {
        return if (Build.VERSION.SDK_INT >= 19) {
            hostView.isAttachedToWindow
        } else {
            hostView.handler != null
        }
    }

    private fun hasAdapter(hostView: RecyclerView): Boolean = hostView.adapter != null

    override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
        if (!isAttachedToWindow(recyclerView) || !hasAdapter(recyclerView)) {
            return false
        }
        mGestureDetector.onTouchEvent(event)
        return false
    }

    override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
        // We can silently track tap and and long presses by silently
        // intercepting touch events in the host RecyclerView.
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    abstract fun performItemClick(parent: RecyclerView, view: View, position: Int, id: Long): Boolean
    abstract fun performItemLongClick(parent: RecyclerView, view: View, position: Int, id: Long): Boolean

    private inner class ItemClickGestureListener(
        private val mHostView: RecyclerView,
    ) : SimpleOnGestureListener() {
        private var mTargetChild: View? = null

        override fun onDown(event: MotionEvent): Boolean {
            val x = event.x.toInt()
            val y = event.y.toInt()
            mTargetChild = mHostView.findChildViewUnder(x.toFloat(), y.toFloat())
            return mTargetChild != null
        }

        override fun onShowPress(event: MotionEvent) {
            mTargetChild?.isPressed = true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            var handled = false
            val target = mTargetChild
            if (target != null) {
                target.isPressed = false
                val position = mHostView.getChildAdapterPosition(target)
                val id = mHostView.adapter!!.getItemId(position)
                handled = performItemClick(mHostView, target, position, id)
                mTargetChild = null
            }
            return handled
        }

        override fun onScroll(
            event: MotionEvent?,
            event2: MotionEvent,
            v: Float,
            v2: Float,
        ): Boolean {
            val target = mTargetChild
            if (target != null) {
                target.isPressed = false
                mTargetChild = null
                return true
            }
            return false
        }

        override fun onLongPress(event: MotionEvent) {
            val target = mTargetChild ?: return
            val position = mHostView.getChildAdapterPosition(target)
            val id = mHostView.adapter!!.getItemId(position)
            val handled = performItemLongClick(mHostView, target, position, id)
            if (handled) {
                target.isPressed = false
                mTargetChild = null
            }
        }
    }
}
