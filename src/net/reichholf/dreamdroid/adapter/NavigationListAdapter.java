/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import net.reichholf.dreamdroid.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

/**
 * @author sre
 *
 */
public class NavigationListAdapter extends ArrayAdapter<int[]> {
	
	int[][] mItems;

	
	/**
	 * @param context
	 * @param textViewResourceId
	 * @param objects
	 */
	public NavigationListAdapter(Context context, int textViewResourceId, int[][] items) {
		super(context, textViewResourceId, items);
		mItems = items;	
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if(view == null){
			LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = li.inflate(R.layout.nav_list_item, null);
		}
		
		int[] item = mItems[position];
		CheckedTextView text = (CheckedTextView) view.findViewById(R.id.text1);
		text.setText(item[1]);
		text.setCompoundDrawablesWithIntrinsicBounds(item[2], 0, 0, 0);
		return view;
	}
	
	

}
