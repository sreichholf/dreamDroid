/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import net.reichholf.dreamdroid.R;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * @author sre
 *
 */
public class NavigationListAdapter extends ArrayAdapter<int[]> {
		
	/**
	 * @param context
	 * @param textViewResourceId
	 * @param objects
	 */
	public NavigationListAdapter(Context context, int[][] items) {
		super(context, R.layout.nav_list_item, items);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if(view == null){
			LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = li.inflate(R.layout.nav_list_item, parent, false);
		}

		int[] item = getItem(position);
		TextView text = (TextView) view.findViewById(android.R.id.text1);
		text.setText(item[1]);
        TypedValue drawable = new TypedValue();
        getContext().getTheme().resolveAttribute(item[2], drawable, true);
        if(drawable != null)
		    text.setCompoundDrawablesWithIntrinsicBounds(drawable.resourceId , 0, 0, 0);
		return view;
	}
}
