package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;

/**
 * Created by Stephan on 03.02.2016.
 */
public class ZapAdapter extends BaseAdapter<ZapAdapter.ZapViewHolder> {
	private static String TAG = ZapAdapter.class.getSimpleName();
	private Context mContext;

	public ZapAdapter(Context context, ArrayList<ExtendedHashMap> data) {
		super(data);
		mContext = context;
	}

	@NonNull
	@Override
	public ZapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.zap_grid_item, parent, false);
		ZapViewHolder zvh = new ZapViewHolder(itemView);
		itemView.setTag(zvh);
		return zvh;
	}

	@Override
	public void onBindViewHolder(@NonNull ZapViewHolder holder, int position) {
		ExtendedHashMap service = mData.get(position);
		if (service != null) {
			holder.serviceName.setVisibility(View.VISIBLE);
			holder.serviceName.setText(service.getString(Service.KEY_NAME));
			Picon.setPiconForView(mContext, holder.picon, service, Statics.TAG_PICON, holder.piconCallback);
		}
	}

	static class ZapViewHolder extends RecyclerView.ViewHolder {
		ImageView picon;
		TextView serviceName;
		Callback piconCallback;

		public ZapViewHolder(View itemView) {
			super(itemView);
			picon = itemView.findViewById(R.id.picon);
			serviceName = itemView.findViewById(android.R.id.text1);
			piconCallback = new Callback() {
				@Override
				public void onSuccess() {
					serviceName.setVisibility(View.GONE);
					picon.setVisibility(View.VISIBLE);
				}

				@Override
				public void onError(Exception e) {
					Log.w(TAG, String.format("Error loading picon for %s", serviceName.getText()));
					serviceName.setVisibility(View.VISIBLE);
					picon.setVisibility(View.GONE);
				}
			};
		}
	}
}
