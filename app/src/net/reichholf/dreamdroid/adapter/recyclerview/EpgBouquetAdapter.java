package net.reichholf.dreamdroid.adapter.recyclerview;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;

import java.util.List;

/**
 * XML multi-service EPG rows bound to typed Event ({@code epg_multi_service_list_item}).
 * Shared by bouquet EPG and EPG search. Detail sheet still receives ExtendedHashMap at the edge.
 */
public class EpgBouquetAdapter extends RecyclerView.Adapter<EpgBouquetAdapter.EventViewHolder> {
	private final List<Event> mData;

	public EpgBouquetAdapter(List<Event> data) {
		mData = data;
	}

	@NonNull
	@Override
	public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.epg_multi_service_list_item, parent, false);
		return new EventViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
		Event event = mData.get(position);
		holder.title.setText(event.getTitle());
		holder.serviceName.setText(event.getServiceName());
		holder.description.setText(event.getDescriptionExtended());
		holder.start.setText(event.getStartReadable());
		holder.duration.setText(event.getDurationReadable());
		Picon.setPiconForView(holder.itemView.getContext(), holder.picon,
				event.getServiceReference(), event.getServiceName(), Statics.TAG_PICON, null);
	}

	@Override
	public int getItemCount() {
		return mData.size();
	}

	static class EventViewHolder extends RecyclerView.ViewHolder {
		final ImageView picon;
		final TextView title;
		final TextView serviceName;
		final TextView description;
		final TextView start;
		final TextView duration;

		EventViewHolder(@NonNull View itemView) {
			super(itemView);
			picon = itemView.findViewById(R.id.picon);
			title = itemView.findViewById(R.id.event_title);
			serviceName = itemView.findViewById(R.id.service_name);
			description = itemView.findViewById(R.id.event_short);
			start = itemView.findViewById(R.id.event_start);
			duration = itemView.findViewById(R.id.event_duration);
		}
	}
}
