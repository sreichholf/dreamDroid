package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;

/**
 * Created by Stephan on 14.05.2015.
 */
public class ServiceAdapter extends BaseAdapter<ServiceAdapter.ServiceViewHolder> {
	protected Context mContext;

	public ServiceAdapter(Context context, ArrayList<ExtendedHashMap> data) {
		super(data);
		mContext = context;
	}


	@NonNull
	@Override
	public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.service_list_item_nn, parent, false);
		itemView.setClickable(true);
		itemView.setLongClickable(true);
		return new ServiceViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
		ExtendedHashMap service = mData.get(position);
		String next = service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE));
		boolean hasNext = next != null && !"".equals(next);

		if (service != null) {
			if (Service.isMarker(service.getString(Service.KEY_REFERENCE))) {
				holder.root.setCardElevation(0);
				holder.root.setClickable(false);
				holder.parentService.setVisibility(View.GONE);
				holder.parentMarker.setVisibility(View.VISIBLE);
				holder.markerName.setText(service.getString(Event.KEY_SERVICE_NAME));
				return;
			}
			Event.supplementReadables(service);
			Picon.setPiconForView(mContext, holder.picon, service, Statics.TAG_PICON);
			holder.root.setCardElevation(mContext.getResources().getDimension(R.dimen.cardview_elevation));
			holder.root.setClickable(false);
			holder.parentService.setVisibility(View.VISIBLE);
			holder.parentMarker.setVisibility(View.GONE);
			holder.serviceName.setText(service.getString(Event.KEY_SERVICE_NAME));
			holder.eventNowTitle.setText(service.getString(Event.KEY_EVENT_TITLE));
			holder.eventNowStart.setText(service.getString(Event.KEY_EVENT_START_TIME_READABLE));
			holder.eventNowDuration.setText(service.getString(Event.KEY_EVENT_DURATION_READABLE));

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

			holder.progress.setVisibility(View.VISIBLE);
			if (max > 0 && cur >= 0) {
				holder.progress.setMax((int) max);
				holder.progress.setProgress((int) cur);
			}

			if (hasNext) {
				holder.parentNext.setVisibility(View.VISIBLE);
				holder.eventNextTitle.setText(service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE)));
				holder.eventNextStart.setText(service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_START_TIME_READABLE)));
				holder.eventNextDuration.setText(service.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_DURATION_READABLE)));
			} else {
				holder.parentNext.setVisibility(View.GONE);
			}
		}
	}

	public class ServiceViewHolder extends RecyclerView.ViewHolder {
		CardView root;
		ImageView picon;
		ProgressBar progress;
		TextView serviceName;
		TextView eventNowTitle;
		TextView eventNowStart;
		TextView eventNowDuration;
		TextView eventNextTitle;
		TextView eventNextStart;
		TextView eventNextDuration;
		TextView markerName;
		View parentService;
		View parentNext;
		View parentMarker;

		public ServiceViewHolder(View itemView) {
			super(itemView);

			root = itemView.findViewById(R.id.service_list_item_nn);
			picon = itemView.findViewById(R.id.picon);
			progress = itemView.findViewById(R.id.service_progress);
			serviceName = itemView.findViewById(R.id.service_name);
			eventNowTitle = itemView.findViewById(R.id.event_now_title);
			eventNowStart = itemView.findViewById(R.id.event_now_start);
			eventNowDuration = itemView.findViewById(R.id.event_now_duration);
			eventNextTitle = itemView.findViewById(R.id.event_next_title);
			eventNextStart = itemView.findViewById(R.id.event_next_start);
			eventNextDuration = itemView.findViewById(R.id.event_next_duration);
			markerName = itemView.findViewById(R.id.marker_name);
			parentService = itemView.findViewById(R.id.parent_service);
			parentMarker = itemView.findViewById(R.id.parent_marker);
			parentNext = itemView.findViewById(R.id.event_next);
		}
	}
}
