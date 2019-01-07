package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import android.widget.SimpleAdapter;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.util.List;
import java.util.Map;

public class SimpleExtendedHashMapAdapter extends SimpleAdapter {
	/**
	 * Constructor
	 *
	 * @param context  The context where the View associated with this SimpleAdapter is running
	 * @param data     A List of Maps. Each entry in the List corresponds to one row in the list. The
	 *                 Maps contain the data for each row, and should include all the entries specified in
	 *                 "from"
	 * @param resource Resource identifier of a view layout that defines the views for this list
	 *                 item. The layout file should include at least those named views defined in "to"
	 * @param from     A list of column names that will be added to the Map associated with each
	 *                 item.
	 * @param to       The views that should display column in the "from" parameter. These should all be
	 *                 TextViews. The first N views in this list are given the values of the first N columns
	 */
	public SimpleExtendedHashMapAdapter(Context context, List<ExtendedHashMap> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
	}
}
