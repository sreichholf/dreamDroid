package net.reichholf.dreamdroid.widget.helper

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

class ItemClickSupport private constructor(private val recyclerView: RecyclerView) {
    private val touchListener: TouchListener?

    private var clickListener: OnItemClickListener? = null
    private var longClickListener: OnItemLongClickListener? = null

    private val onClickListener = View.OnClickListener { v ->
        val listener = clickListener
        if (listener != null) {
            val holder = recyclerView.getChildViewHolder(v)
            listener.onItemClick(recyclerView, v, holder.bindingAdapterPosition, v.id.toLong())
        }
    }

    private val onLongClickListener = View.OnLongClickListener { v ->
        val listener = longClickListener
        if (listener != null) {
            val holder = recyclerView.getChildViewHolder(v)
            return@OnLongClickListener listener.onItemLongClick(
                recyclerView,
                v,
                holder.bindingAdapterPosition,
                v.id.toLong()
            )
        }
        false
    }

    private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            if (clickListener != null) {
                view.setOnClickListener(onClickListener)
            }
            if (longClickListener != null) {
                view.setOnLongClickListener(onLongClickListener)
            }
        }

        override fun onChildViewDetachedFromWindow(view: View) {}
    }

    init {
        // the ID must be declared in XML, used to avoid
        // replacing the ItemClickSupport without removing
        // the old one from the RecyclerView
        recyclerView.setTag(R.id.recyclerview_item_click_support, this)
        if (DreamDroid.isTV(recyclerView.context)) {
            recyclerView.addOnChildAttachStateChangeListener(attachListener)
            touchListener = null
        } else {
            touchListener = TouchListener(recyclerView)
            recyclerView.addOnItemTouchListener(touchListener)
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener?): ItemClickSupport {
        clickListener = listener
        return this
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener?): ItemClickSupport {
        longClickListener = listener
        return this
    }

    private fun detach(view: RecyclerView) {
        if (touchListener != null) {
            view.removeOnItemTouchListener(touchListener)
        } else {
            view.removeOnChildAttachStateChangeListener(attachListener)
        }
        view.setTag(R.id.recyclerview_item_click_support, null)
    }

    fun interface OnItemClickListener {
        fun onItemClick(recyclerView: RecyclerView, v: View, position: Int, id: Long)
    }

    fun interface OnItemLongClickListener {
        fun onItemLongClick(recyclerView: RecyclerView, v: View, position: Int, id: Long): Boolean
    }

    private inner class TouchListener(recyclerView: RecyclerView) :
        ClickItemTouchListener(recyclerView) {
        override fun performItemClick(
            parent: RecyclerView,
            view: View,
            position: Int,
            id: Long
        ): Boolean {
            val listener = clickListener
            if (listener != null && position >= 0) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                listener.onItemClick(parent, view, position, id)
                return true
            }
            return false
        }

        override fun performItemLongClick(
            parent: RecyclerView,
            view: View,
            position: Int,
            id: Long
        ): Boolean {
            val listener = longClickListener
            if (listener != null && position >= 0) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                return listener.onItemLongClick(parent, view, position, id)
            }
            return false
        }
    }

    companion object {
        fun addTo(view: RecyclerView): ItemClickSupport {
            var support = view.getTag(R.id.recyclerview_item_click_support) as ItemClickSupport?
            if (support == null) {
                support = ItemClickSupport(view)
            }
            return support
        }

        fun removeFrom(view: RecyclerView): ItemClickSupport? {
            val support = view.getTag(R.id.recyclerview_item_click_support) as ItemClickSupport?
            support?.detach(view)
            return support
        }
    }
}
