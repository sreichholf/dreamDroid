/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * @author sre
 * 
 */
public class ServiceListAdapter extends ArrayAdapter<ExtendedHashMap> {

	/**
	 * @param context
	 * @param textViewResourceId
	 * @param services
	 */
	public ServiceListAdapter(Context context, int textViewResourceId, ArrayList<ExtendedHashMap> services) {
		super(context, textViewResourceId, services);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if (view == null) {
			LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			if (DreamDroid.featureNowNext()){
				view = li.inflate(R.layout.service_list_item_nn, null);
			} else {
				view = li.inflate(R.layout.service_list_item, null);
			}
		}

		ExtendedHashMap service = getItem(position);
		if (service != null) {
			setTextView(view, R.id.service_name, service.getString(Event.KEY_SERVICE_NAME));
			setTextView(view, R.id.event_now_title, service.getString(Event.KEY_EVENT_TITLE));
			setTextView(view, R.id.event_now_start, service.getString(Event.KEY_EVENT_START_TIME_READABLE));
			setTextView(view, R.id.event_now_duration, service.getString(Event.KEY_EVENT_DURATION_READABLE));
			ProgressBar progress = (ProgressBar) view.findViewById(R.id.service_progress);

			long max = -1;
			long cur = -1;
			String duration = service.getString(Event.KEY_EVENT_DURATION);
			String start = service.getString(Event.KEY_EVENT_START);

			if(duration != null && start != null){
				try {
					max = Double.valueOf(duration).longValue() / 60;
					cur = max - DateTime.getRemaining(duration, start);
				} catch (Exception e) {}
			}
			
			if(max > 0 && cur >= 0){
				progress.setVisibility(View.VISIBLE);
				progress.setMax((int) max);
				progress.setProgress((int) cur);
			} else {
				progress.setVisibility(View.GONE);
			}

			if (DreamDroid.featureNowNext()) {
				setTextView(view, R.id.event_next_title,
						service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE)));
				setTextView(view, R.id.event_next_start,
						service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_START_TIME_READABLE)));
				setTextView(view, R.id.event_next_duration,
						service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_DURATION_READABLE)));
			}
		}

		return view;
	}

	private void setTextView(View view, int id, String value) {
		TextView tv = (TextView) view.findViewById(id);
		if (tv != null)
			tv.setText(value);
	}

}
