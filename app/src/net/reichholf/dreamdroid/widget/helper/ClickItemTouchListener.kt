package net.reichholf.dreamdroid.widget.helper

import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener

abstract class ClickItemTouchListener(hostView: RecyclerView) : OnItemTouchListener {
    private val gestureDetector: GestureDetector =
        GestureDetector(hostView.context, ItemClickGestureListener(hostView))

    private fun isAttachedToWindow(hostView: RecyclerView): Boolean = hostView.isAttachedToWindow

    private fun hasAdapter(hostView: RecyclerView): Boolean = hostView.adapter != null

    override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
        if (!isAttachedToWindow(recyclerView) || !hasAdapter(recyclerView)) {
            return false
        }
        gestureDetector.onTouchEvent(event)
        return false
    }

    override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
        // We can silently track tap and and long presses by silently
        // intercepting touch events in the host RecyclerView.
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    abstract fun performItemClick(
        parent: RecyclerView,
        view: View,
        position: Int,
        id: Long
    ): Boolean
    abstract fun performItemLongClick(
        parent: RecyclerView,
        view: View,
        position: Int,
        id: Long
    ): Boolean

    private inner class ItemClickGestureListener(private val hostView: RecyclerView) :
        SimpleOnGestureListener() {
        private var targetChild: View? = null

        override fun onDown(event: MotionEvent): Boolean {
            val x = event.x.toInt()
            val y = event.y.toInt()
            targetChild = hostView.findChildViewUnder(x.toFloat(), y.toFloat())
            return targetChild != null
        }

        override fun onShowPress(event: MotionEvent) {
            targetChild?.isPressed = true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            var handled = false
            val target = targetChild
            if (target != null) {
                target.isPressed = false
                val position = hostView.getChildAdapterPosition(target)
                val id = hostView.adapter!!.getItemId(position)
                handled = performItemClick(hostView, target, position, id)
                targetChild = null
            }
            return handled
        }

        override fun onScroll(
            event: MotionEvent?,
            event2: MotionEvent,
            v: Float,
            v2: Float
        ): Boolean {
            val target = targetChild
            if (target != null) {
                target.isPressed = false
                targetChild = null
                return true
            }
            return false
        }

        override fun onLongPress(event: MotionEvent) {
            val target = targetChild ?: return
            val position = hostView.getChildAdapterPosition(target)
            val id = hostView.adapter!!.getItemId(position)
            val handled = performItemLongClick(hostView, target, position, id)
            if (handled) {
                target.isPressed = false
                targetChild = null
            }
        }
    }
}
