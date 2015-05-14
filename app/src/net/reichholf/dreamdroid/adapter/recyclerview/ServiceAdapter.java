package net.reichholf.dreamdroid.adapter.recyclerview;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.util.ArrayList;

/**
 * Created by Stephan on 14.05.2015.
 */
public class ServiceAdapter extends BaseAdapter<ServiceAdapter.ServiceViewHolder> {
	public ServiceAdapter(ArrayList<ExtendedHashMap> data) {
		super(data);
	}

	@Override
	public ServiceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return null;
	}

	@Override
	public void onBindViewHolder(ServiceViewHolder holder, int position) {

	}

	public class ServiceViewHolder extends RecyclerView.ViewHolder{

		public ServiceViewHolder(View itemView) {
			super(itemView);
		}
	}
}
