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
import net.reichholf.dreamdroid.helpers.ImageLoader;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * @author sre
 * 
 */
public class ServiceListAdapter extends ArrayAdapter<ExtendedHashMap> {
	@SuppressWarnings("unused")
	private static String LOG_TAG = "ServiceListAdapter";
	private ImageLoader mImageLoader;
	private LayoutInflater mInflater;

	/**
	 * @param context
	 * @param textViewResourceId
	 * @param services
	 */
	public ServiceListAdapter(Context context, ArrayList<ExtendedHashMap> services) {
		super(context, 0, services);
		mImageLoader = new ImageLoader();
		mImageLoader.setMode(ImageLoader.Mode.CORRECT);
		mInflater = LayoutInflater.from(context);
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		ExtendedHashMap service = getItem(position);
		return !Service.isMarker(service.getString(Service.KEY_REFERENCE));
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		ExtendedHashMap service = getItem(position);
		String next = service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE));
		String now = service.getString(Event.KEY_EVENT_TITLE);

		boolean hasNext = next != null && !"".equals(next);
		boolean hasNow = now != null && !"".equals(now);

		int viewId = android.R.id.text1;
		int layoutId = R.layout.simple_list_item_1;

		if (Service.isMarker(service.getString(Service.KEY_REFERENCE))) {
			layoutId = R.layout.service_list_marker;
			hasNow = false;
		} else {
			if (hasNext) {
				viewId = R.id.service_list_item_nn;
				layoutId = R.layout.service_list_item_nn;
			} else if (hasNow) {
				viewId = R.id.service_list_item;
				layoutId = R.layout.service_list_item;
			}
		}

		ServiceViewHolder viewHolder = null;
		if (view == null || view.getId() != viewId) {
			view = mInflater.inflate(layoutId, parent, false);
			viewHolder = new ServiceViewHolder();
			if (hasNow) {
				viewHolder.picon = (ImageView) view.findViewById(R.id.picon);
				viewHolder.progress = (ProgressBar) view.findViewById(R.id.service_progress);
				viewHolder.serviceName = (TextView) view.findViewById(R.id.service_name);
				viewHolder.eventNowTitle = (TextView) view.findViewById(R.id.event_now_title);
				viewHolder.eventNowStart = (TextView) view.findViewById(R.id.event_now_start);
				viewHolder.eventNowDuration = (TextView) view.findViewById(R.id.event_now_duration);
				if (hasNext) {
					viewHolder.eventNextTitle = (TextView) view.findViewById(R.id.event_next_title);
					viewHolder.eventNextStart = (TextView) view.findViewById(R.id.event_next_start);
					viewHolder.eventNextDuration = (TextView) view.findViewById(R.id.event_next_duration);
				}
			} else {
				viewHolder.serviceName = (TextView) view.findViewById(android.R.id.text1);
			}
			view.setTag(viewHolder);
		} else {
			viewHolder = (ServiceViewHolder) view.getTag();
		}

		Picon.setPiconForView(getContext(), viewHolder.picon, mImageLoader, service);

		if (service != null) {
			if (!hasNow) {
				setTextView(viewHolder.serviceName, service.getString(Event.KEY_SERVICE_NAME));
				return view;
			}
			setTextView(viewHolder.serviceName, service.getString(Event.KEY_SERVICE_NAME));
			setTextView(viewHolder.eventNowTitle, service.getString(Event.KEY_EVENT_TITLE));
			setTextView(viewHolder.eventNowStart, service.getString(Event.KEY_EVENT_START_TIME_READABLE));
			setTextView(viewHolder.eventNowDuration, service.getString(Event.KEY_EVENT_DURATION_READABLE));

			long max = -1;
			long cur = -1;
			String duration = service.getString(Event.KEY_EVENT_DURATION);
			String start = service.getString(Event.KEY_EVENT_START);

			if (duration != null && start != null && !Python.NONE.equals(duration) && !Python.NONE.equals(start)) {
				try {
					max = Double.valueOf(duration).longValue() / 60;
					cur = max - DateTime.getRemaining(duration, start);
				} catch (Exception e) {
					Log.e(DreamDroid.LOG_TAG, e.toString());
				}
			}

			if (max > 0 && cur >= 0) {
				viewHolder.progress.setVisibility(View.VISIBLE);
				viewHolder.progress.setMax((int) max);
				viewHolder.progress.setProgress((int) cur);
			} else {
				viewHolder.progress.setVisibility(View.GONE);
			}

			if (hasNext) {
				setTextView(viewHolder.eventNextTitle,
						service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE)));
				setTextView(viewHolder.eventNextStart,
						service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_START_TIME_READABLE)));
				setTextView(viewHolder.eventNextDuration,
						service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_DURATION_READABLE)));
			}
		}

		return view;
	}

	private void setTextView(TextView tv, String value) {
		if (tv != null)
			tv.setText(value);
	}

	static class ServiceViewHolder {
		ImageView picon;
		ProgressBar progress;
		TextView serviceName;
		TextView eventNowTitle;
		TextView eventNowStart;
		TextView eventNowDuration;
		TextView eventNextTitle;
		TextView eventNextStart;
		TextView eventNextDuration;
		TextView eventTitle;
	}

}
