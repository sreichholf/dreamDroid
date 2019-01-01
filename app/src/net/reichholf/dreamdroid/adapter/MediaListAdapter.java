/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Mediaplayer;

import java.util.ArrayList;

/**
 * @author asc
 */
public class MediaListAdapter extends ArrayAdapter<ExtendedHashMap> {

	/**
	 * @param context
	 * @param items
	 */
	public MediaListAdapter(Context context, ArrayList<ExtendedHashMap> items) {
		super(context, 0, items);

	}

	@NonNull
	@Override
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		View view = convertView;
		if (view == null) {
			LayoutInflater li = LayoutInflater.from(getContext());
			view = li.inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		ExtendedHashMap media = getItem(position);
		if (media != null) {
			TextView textView = view.findViewById(android.R.id.text1);
			String root = media.getString(Mediaplayer.KEY_ROOT);
			String isDirectory = media.getString(Mediaplayer.KEY_IS_DIRECTORY);
			String reference = media.getString(Mediaplayer.KEY_SERVICE_REFERENCE);

			if (Python.NONE.equals(root)) {
				textView.setText(reference);
			} else if (Python.TRUE.equals(isDirectory)) {
				textView.setText(getShortName(reference) + "/");
			} else {
				textView.setText(getShortName(reference));
			}
		}
		return view;
	}

	public String getShortName(String path) {
		String[] split = path.split("/");
		return split[split.length - 1];
	}
}
