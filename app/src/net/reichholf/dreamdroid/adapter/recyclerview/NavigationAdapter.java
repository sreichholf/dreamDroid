package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;

/**
 * Created by Stephan on 23.05.2015.
 */
public class NavigationAdapter extends RecyclerView.Adapter<NavigationAdapter.NavigationViewHolder> {

	private Context mContext;
	private int[][] mItems;

	public NavigationAdapter(Context context, int[][] items) {
		mContext = context;
		mItems = items;
	}

	@Override
	public NavigationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.nav_list_item, parent, false);
		return new NavigationViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(NavigationViewHolder holder, int position) {
		int[] item = mItems[position];
		holder.text1.setText(item[1]);

		TypedValue drawable = new TypedValue();
		mContext.getTheme().resolveAttribute(item[2], drawable, true);
		if (drawable != null)
			holder.text1.setCompoundDrawablesWithIntrinsicBounds(drawable.resourceId, 0, 0, 0);
	}

	@Override
	public int getItemCount() {
		return mItems.length;
	}

	public class NavigationViewHolder extends RecyclerView.ViewHolder {
		public TextView text1;

		public NavigationViewHolder(View itemView) {
			super(itemView);
			text1 = (TextView) itemView.findViewById(android.R.id.text1);
		}
	}
}
