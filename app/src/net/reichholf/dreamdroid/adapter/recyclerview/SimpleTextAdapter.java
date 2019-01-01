package net.reichholf.dreamdroid.adapter.recyclerview;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Stephan on 14.05.2015.
 */
public class SimpleTextAdapter extends BaseAdapter<SimpleTextAdapter.SimpleViewHolder> {
	int mLayoutId;
	int[] mIds;
	String[] mKeys;

	public SimpleTextAdapter(ArrayList<ExtendedHashMap> data, int layoutId, String[] keys, int[] ids) {
		super(data);
		mLayoutId = layoutId;
		mIds = ids;
		mKeys = keys;
	}

	@NonNull
	@Override
	public SimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(mLayoutId, parent, false);
		return new SimpleViewHolder(itemView, mIds);
	}

	@Override
	public void onBindViewHolder(@NonNull SimpleViewHolder holder, int position) {
		for (int i = 0; i < mIds.length; ++i) {
			TextView view = holder.getView(mIds[i]);
			view.setText(mData.get(position).getString(mKeys[i]));
		}
	}

	public class SimpleViewHolder extends RecyclerView.ViewHolder {
		HashMap<Integer, TextView> mViews;

		public SimpleViewHolder(View itemView, int[] ids) {
			super(itemView);
			mViews = new HashMap<>();
			for (int id : ids) {
				mViews.put(id, itemView.findViewById(id));
			}
		}

		public TextView getView(int id) {
			return mViews.get(id);
		}
	}
}
