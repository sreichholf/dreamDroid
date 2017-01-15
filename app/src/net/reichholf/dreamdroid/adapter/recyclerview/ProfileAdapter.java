package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.ProfileListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.util.ArrayList;

/**
 * Created by Stephan on 03.05.2015.
 */
public class ProfileAdapter extends BaseAdapter<ProfileAdapter.ProfileViewHolder> {

	protected int mActiveColor;


	public ProfileAdapter(Context context, ArrayList<ExtendedHashMap> data) {
		super(data);
		mActiveColor = ContextCompat.getColor(context, R.color.active_profile_color);
	}

	@Override
	public ProfileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.two_line_card_list_item, parent, false);
		return new ProfileViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(ProfileViewHolder holder, int position) {
		ExtendedHashMap ehm = mData.get(position);
		Boolean isActive = (Boolean) ehm.get(ProfileListFragment.KEY_ACTIVE_PROFILE);

		String profile = ehm.getString(DatabaseHelper.KEY_PROFILE_PROFILE);
		String host = ehm.getString(DatabaseHelper.KEY_PROFILE_HOST);

		holder.text1.setText(profile);
		holder.text2.setText(host);

		if (isActive) {
			holder.indicator.setVisibility(View.VISIBLE);
			holder.indicator.setBackgroundColor(mActiveColor);
		} else {
			holder.indicator.setVisibility(View.GONE);
		}
	}

	public class ProfileViewHolder extends RecyclerView.ViewHolder {
		public TextView text1;
		public TextView text2;
		public TextView indicator;

		public ProfileViewHolder(View itemView) {
			super(itemView);
			text1 = (TextView) itemView.findViewById(android.R.id.text1);
			text2 = (TextView) itemView.findViewById(android.R.id.text2);
			indicator = (TextView) itemView.findViewById(R.id.activeIndicator);
		}
	}
}
