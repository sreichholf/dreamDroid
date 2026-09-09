package net.reichholf.dreamdroid.adapter.recyclerview;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.enigma.Service;

import java.util.List;

/**
 * Simple one-line list of typed {@link Service} names (bouquet picker).
 */
public class ServiceNameAdapter extends RecyclerView.Adapter<ServiceNameAdapter.ServiceNameViewHolder> {
	private final List<Service> mData;
	private final int mLayoutId;

	public ServiceNameAdapter(@NonNull List<Service> data, int layoutId) {
		mData = data;
		mLayoutId = layoutId;
	}

	@NonNull
	@Override
	public ServiceNameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View itemView = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
		return new ServiceNameViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull ServiceNameViewHolder holder, int position) {
		Service service = mData.get(position);
		holder.name.setText(service != null ? service.getName() : "");
	}

	@Override
	public int getItemCount() {
		return mData.size();
	}

	static class ServiceNameViewHolder extends RecyclerView.ViewHolder {
		final TextView name;

		ServiceNameViewHolder(@NonNull View itemView) {
			super(itemView);
			name = itemView.findViewById(android.R.id.text1);
		}
	}
}
