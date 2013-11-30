package net.reichholf.dreamdroid.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.ProfileListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.util.List;
import java.util.Map;

/**
 * Created by Georg
 */
public class ProfileListSimpleAdapter extends SimpleAdapter {

	private final Context mContext;
	private int mActiveColor;

	public ProfileListSimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
		mContext = context;
		mActiveColor = mContext.getResources().getColor(R.color.active_profile_color);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final View view = super.getView(position, convertView, parent);
		Resources res = mContext.getResources();
		if (res != null && view != null) {
			ExtendedHashMap ehm = (ExtendedHashMap) getItem(position);
			Boolean isActive = (Boolean) ehm.get(ProfileListFragment.KEY_ACTIVE_PROFILE);

			TextView indicator = (TextView) view.findViewById(R.id.activeIndicator);
			if (isActive) {
				indicator.setVisibility(View.VISIBLE);
				indicator.setBackgroundColor(mActiveColor);
			} else {
				indicator.setVisibility(View.GONE);
			}
		}
		return view;
	}
}
