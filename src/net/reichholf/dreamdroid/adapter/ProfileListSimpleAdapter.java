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

    private final Context context;

    public ProfileListSimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
        super(context, data, resource, from, to);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View row = super.getView(position, convertView, parent);
        Resources res = context.getResources();
        if (res != null && row != null) {
            ExtendedHashMap ehm = (ExtendedHashMap) getItem(position);
            Boolean isActive = (Boolean)ehm.get(ProfileListFragment.KEY_ACTIVE_PROFILE);

            TextView activeTextView = (TextView) row.findViewById(R.id.activeIndicator);
            if (isActive) {
                activeTextView.setVisibility(View.VISIBLE);
                int color = context.getResources().getColor(R.color.active_profile_color);
                activeTextView.setBackgroundColor(color);
            } else {
                activeTextView.setVisibility(View.GONE);
            }
        }
        return row;
    }
}
