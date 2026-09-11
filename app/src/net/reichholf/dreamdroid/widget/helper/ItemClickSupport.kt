package net.reichholf.dreamdroid.widget.helper

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

class ItemClickSupport private constructor(recyclerView: RecyclerView) {
    private val mRecyclerView: RecyclerView = recyclerView
    private val mTouchListener: TouchListener?
    private var mOnItemClickListener: OnItemClickListener? = null
    private var mOnItemLongClickListener: OnItemLongClickListener? = null

    private val mOnClickListener = View.OnClickListener { v ->
        val listener = mOnItemClickListener
        if (listener != null) {
            val holder = mRecyclerView.getChildViewHolder(v)
            listener.onItemClick(mRecyclerView, v, holder.adapterPosition, v.id.toLong())
        }
    }

    private val mOnLongClickListener = View.OnLongClickListener { v ->
        val listener = mOnItemLongClickListener
        if (listener != null) {
            val holder = mRecyclerView.getChildViewHolder(v)
            return@OnLongClickListener listener.onItemLongClick(
                mRecyclerView,
                v,
                holder.adapterPosition,
                v.id.toLong(),
            )
        }
        false
    }

    private val mAttachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            if (mOnItemClickListener != null) {
                view.setOnClickListener(mOnClickListener)
            }
            if (mOnItemLongClickListener != null) {
                view.setOnLongClickListener(mOnLongClickListener)
            }
        }

        override fun onChildViewDetachedFromWindow(view: View) {}
    }

    init {
        // the ID must be declared in XML, used to avoid
        // replacing the ItemClickSupport without removing
        // the old one from the RecyclerView
        mRecyclerView.setTag(R.id.recyclerview_item_click_support, this)
        if (DreamDroid.isTV(recyclerView.context)) {
            mRecyclerView.addOnChildAttachStateChangeListener(mAttachListener)
            mTouchListener = null
        } else {
            mTouchListener = TouchListener(recyclerView)
            recyclerView.addOnItemTouchListener(mTouchListener)
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener?): ItemClickSupport {
        mOnItemClickListener = listener
        return this
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener?): ItemClickSupport {
        mOnItemLongClickListener = listener
        return this
    }

    private fun detach(view: RecyclerView) {
        if (mTouchListener != null) {
            view.removeOnItemTouchListener(mTouchListener)
        } else {
            view.removeOnChildAttachStateChangeListener(mAttachListener)
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
            id: Long,
        ): Boolean {
            val listener = mOnItemClickListener
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
            id: Long,
        ): Boolean {
            val listener = mOnItemLongClickListener
            if (listener != null && position >= 0) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                return listener.onItemLongClick(parent, view, position, id)
            }
            return false
        }
    }

    companion object {
        @JvmStatic
        fun addTo(view: RecyclerView): ItemClickSupport {
            var support = view.getTag(R.id.recyclerview_item_click_support) as ItemClickSupport?
            if (support == null) {
                support = ItemClickSupport(view)
            }
            return support
        }

        @JvmStatic
        fun removeFrom(view: RecyclerView): ItemClickSupport? {
            val support = view.getTag(R.id.recyclerview_item_click_support) as ItemClickSupport?
            support?.detach(view)
            return support
        }
    }
}
