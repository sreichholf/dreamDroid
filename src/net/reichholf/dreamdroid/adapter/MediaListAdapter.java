/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Mediaplayer;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * @author asc
 * 
 */
public class MediaListAdapter extends ArrayAdapter<ExtendedHashMap> {
	
	/**
	 * @param context
	 * @param textViewResourceId
	 * @param objects
	 */
	public MediaListAdapter(Context context, int textViewResourceId, ArrayList<ExtendedHashMap> items) {
		super(context, textViewResourceId, items);

	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if (view == null) {
			LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = li.inflate(R.layout.media_item, null);
		}

		ExtendedHashMap media = getItem(position);
		if (media != null) {
			TextView mediaName = (TextView) view.findViewById(R.id.media_name);

			String root = media.getString(Mediaplayer.KEY_ROOT);
			String isDirectory = media.getString(Mediaplayer.KEY_IS_DIRECTORY);
			String reference = media.getString(Mediaplayer.KEY_SERVICE_REFERENCE);
			
			if (root.equals("None")) {
				mediaName.setText(media.getString(Mediaplayer.KEY_SERVICE_REFERENCE));
			}
			else if (root.equals("playlist")){
				mediaName.setText(media.getString(Mediaplayer.KEY_SERVICE_REFERENCE));
			}
			else if (isDirectory.equals("False")){
				int pos = reference.lastIndexOf("/");
				mediaName.setText(reference.substring(pos + 1));
			}
			else {
				String path = media.getString(Mediaplayer.KEY_SERVICE_REFERENCE);
				path = path.substring(0, path.length()-1);
				int pos = path.lastIndexOf("/");
				path = path.substring(pos+1);
				mediaName.setText(path + "/");
			}
		}
	
		return view;
	}

}
