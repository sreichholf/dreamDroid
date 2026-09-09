package net.reichholf.dreamdroid.adapter.recyclerview;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Event;

import java.util.List;

/**
 * XML EPG rows bound to typed Event. Detail sheet still receives ExtendedHashMap at the edge.
 */
public class ServiceEpgAdapter extends RecyclerView.Adapter<ServiceEpgAdapter.EventViewHolder> {
	private final List<Event> mData;

	public ServiceEpgAdapter(List<Event> data) {
		mData = data;
	}

	@NonNull
	@Override
	public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.epg_list_item, parent, false);
		return new EventViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
		Event event = mData.get(position);
		holder.title.setText(event.getTitle());
		holder.description.setText(event.getDescriptionExtended());
		holder.start.setText(event.getStartReadable());
		holder.duration.setText(event.getDurationReadable());
	}

	@Override
	public int getItemCount() {
		return mData.size();
	}

	static class EventViewHolder extends RecyclerView.ViewHolder {
		final TextView title;
		final TextView description;
		final TextView start;
		final TextView duration;

		EventViewHolder(@NonNull View itemView) {
			super(itemView);
			title = itemView.findViewById(R.id.event_title);
			description = itemView.findViewById(R.id.event_short);
			start = itemView.findViewById(R.id.event_start);
			duration = itemView.findViewById(R.id.event_duration);
		}
	}
}
