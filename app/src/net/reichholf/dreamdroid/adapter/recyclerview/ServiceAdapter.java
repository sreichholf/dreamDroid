package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.material.card.MaterialCardView;
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
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.ServiceNowNext;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;

/**
 * Created by Stephan on 14.05.2015.
 */
public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {
	protected Context mContext;
	protected ArrayList<ServiceNowNext> mData;

	public ServiceAdapter(Context context, ArrayList<ServiceNowNext> data) {
		mContext = context;
		mData = data;
	}

	@Override
	public int getItemCount() {
		return mData.size();
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
		ServiceNowNext service = mData.get(position);
		Event nextEvent = service.getNext();
		String next = nextEvent != null ? nextEvent.getTitle() : null;
		boolean hasNext = next != null && !"".equals(next);

		String ref = service.getServiceReference();
		if (Service.isMarker(ref)) {
			holder.root.setCardElevation(0);
			holder.root.setClickable(false);
			holder.parentService.setVisibility(View.GONE);
			holder.parentMarker.setVisibility(View.VISIBLE);
			holder.markerName.setText(service.getServiceName());
			return;
		}
		if (Service.isDirectory(ref)) {
			holder.parentService.setVisibility(View.VISIBLE);
			holder.parentMarker.setVisibility(View.GONE);
			holder.parentNow.setVisibility(View.GONE);
			holder.parentNext.setVisibility(View.GONE);
			holder.picon.setVisibility(View.GONE);
			holder.progress.setVisibility(View.GONE);
			holder.root.setCardElevation(mContext.getResources().getDimension(R.dimen.cardview_elevation));
			holder.root.setClickable(false);
			holder.serviceName.setText(service.getServiceName());
			return;
		}
		holder.parentNow.setVisibility(View.VISIBLE);

		Picon.setPiconForView(mContext, holder.picon, ref, service.getServiceName(), Statics.TAG_PICON, null);
		holder.root.setCardElevation(mContext.getResources().getDimension(R.dimen.cardview_elevation));
		holder.root.setClickable(false);
		holder.parentService.setVisibility(View.VISIBLE);
		holder.parentMarker.setVisibility(View.GONE);
		holder.serviceName.setText(service.getServiceName());

		Event now = service.getNow();
		holder.eventNowTitle.setText(now != null ? now.getTitle() : null);
		holder.eventNowStart.setText(now != null ? now.getStartTimeReadable() : null);
		holder.eventNowDuration.setText(now != null ? now.getDurationReadable() : null);

		long max = -1;
		long cur = -1;

		if (now != null) {
			String nowTime = now.getCurrentTime();
			String duration = now.getDuration();
			String start = now.getStart();

			if (duration != null && start != null && !Python.NONE.equals(duration) && !Python.NONE.equals(start)
					&& !duration.isEmpty() && !start.isEmpty()) {
				try {
					max = Double.valueOf(duration).longValue() / 60;
					cur = max - DateTime.getRemaining(duration, start, nowTime);
				} catch (Exception e) {
					Log.e(DreamDroid.LOG_TAG, e.toString());
				}
			}
		}

		holder.progress.setVisibility(View.VISIBLE);
		if (max > 0 && cur >= 0) {
			holder.progress.setMax((int) max);
			holder.progress.setProgress((int) cur);
		}

		if (hasNext) {
			holder.parentNext.setVisibility(View.VISIBLE);
			holder.eventNextTitle.setText(nextEvent.getTitle());
			holder.eventNextStart.setText(nextEvent.getStartTimeReadable());
			holder.eventNextDuration.setText(nextEvent.getDurationReadable());
		} else {
			holder.parentNext.setVisibility(View.GONE);
		}
	}

	public class ServiceViewHolder extends RecyclerView.ViewHolder {
		MaterialCardView root;
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
		View parentNow;
		View parentNext;
		View parentMarker;

		public ServiceViewHolder(@NonNull View itemView) {
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
			parentNow = itemView.findViewById(R.id.event_now);
			parentNext = itemView.findViewById(R.id.event_next);
		}
	}
}
