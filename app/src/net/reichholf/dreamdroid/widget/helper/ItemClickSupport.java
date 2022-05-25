package net.reichholf.dreamdroid.widget.helper;

import android.view.HapticFeedbackConstants;
import android.view.SoundEffectConstants;
import android.view.View;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class ItemClickSupport {
    @NonNull
	private final RecyclerView mRecyclerView;
    @Nullable
	private final TouchListener mTouchListener;
    private OnItemClickListener mOnItemClickListener;
    private OnItemLongClickListener mOnItemLongClickListener;
    @NonNull
	private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            if (mOnItemClickListener != null) {
                RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(v);
                mOnItemClickListener.onItemClick(mRecyclerView, v, holder.getAdapterPosition(), v.getId());
            }
        }
    };
    @NonNull
	private View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(@NonNull View v) {
            if (mOnItemLongClickListener != null) {
                RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(v);
                return mOnItemLongClickListener.onItemLongClick(mRecyclerView, v, holder.getAdapterPosition(), v.getId());
            }
            return false;
        }
    };
    @NonNull
	private RecyclerView.OnChildAttachStateChangeListener mAttachListener
            = new RecyclerView.OnChildAttachStateChangeListener() {
        @Override
        public void onChildViewAttachedToWindow(@NonNull View view) {
            if (mOnItemClickListener != null) {
                view.setOnClickListener(mOnClickListener);
            }
            if (mOnItemLongClickListener != null) {
                view.setOnLongClickListener(mOnLongClickListener);
            }
        }

        @Override
        public void onChildViewDetachedFromWindow(View view) {

        }
    };

    private ItemClickSupport(@NonNull RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        // the ID must be declared in XML, used to avoid
        // replacing the ItemClickSupport without removing
        // the old one from the RecyclerView
        mRecyclerView.setTag(R.id.recyclerview_item_click_support, this);
        if (DreamDroid.isTV(recyclerView.getContext())) {
            mRecyclerView.addOnChildAttachStateChangeListener(mAttachListener);
            mTouchListener = null;
        } else {
            mTouchListener = new TouchListener(recyclerView);
            recyclerView.addOnItemTouchListener(mTouchListener);
        }
    }

    @NonNull
	public static ItemClickSupport addTo(@NotNull RecyclerView view) {
        ItemClickSupport support = (ItemClickSupport) view.getTag(R.id.recyclerview_item_click_support);
        if (support == null) {
            support = new ItemClickSupport(view);
        }
        return support;
    }

    @NonNull
	public static ItemClickSupport removeFrom(@NotNull RecyclerView view) {
        ItemClickSupport support = (ItemClickSupport) view.getTag(R.id.recyclerview_item_click_support);
        if (support != null) {
            support.detach(view);
        }
        return support;
    }

    @NonNull
	public ItemClickSupport setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
        return this;
    }

    @NonNull
	public ItemClickSupport setOnItemLongClickListener(OnItemLongClickListener listener) {
        mOnItemLongClickListener = listener;
        return this;
    }

    private void detach(@NonNull RecyclerView view) {
        if (mTouchListener != null)
            view.removeOnItemTouchListener(mTouchListener);
        else
            view.removeOnChildAttachStateChangeListener(mAttachListener);
        view.setTag(R.id.recyclerview_item_click_support, null);
    }

    public interface OnItemClickListener {
        void onItemClick(RecyclerView recyclerView, View v, int position, long id);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(RecyclerView recyclerView, View v, int position, long id);
    }

    private class TouchListener extends ClickItemTouchListener {
        TouchListener(@NonNull RecyclerView recyclerView) {
            super(recyclerView);
        }

        @Override
        boolean performItemClick(RecyclerView parent, @NonNull View view, int position, long id) {
            if (mOnItemClickListener != null && position >= 0) {
                view.playSoundEffect(SoundEffectConstants.CLICK);
                mOnItemClickListener.onItemClick(parent, view, position, id);
                return true;
            }

            return false;
        }

        @Override
        boolean performItemLongClick(RecyclerView parent, @NonNull View view, int position, long id) {
            if (mOnItemLongClickListener != null && position >= 0) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return mOnItemLongClickListener.onItemLongClick(parent, view, position, id);
            }
            return false;
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean b) {
        }
    }
}