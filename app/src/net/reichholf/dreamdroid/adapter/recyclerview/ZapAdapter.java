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
import android.widget.TextView;

import com.squareup.picasso.Callback;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;

import java.util.List;

/**
 * Created by Stephan on 03.02.2016.
 */
public class ZapAdapter extends RecyclerView.Adapter<ZapAdapter.ZapViewHolder> {
	@NonNull
	private static String TAG = ZapAdapter.class.getSimpleName();
	private Context mContext;
	private List<Service> mData;

	public ZapAdapter(Context context, List<Service> data) {
		mContext = context;
		mData = data;
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
		Service service = mData.get(position);
		if (service != null) {
			holder.serviceName.setVisibility(View.VISIBLE);
			holder.serviceName.setText(service.getName());
			Picon.setPiconForView(mContext, holder.picon, service.getReference(), service.getName(), Statics.TAG_PICON, holder.piconCallback);
		}
	}

	@Override
	public int getItemCount() {
		return mData.size();
	}

	static class ZapViewHolder extends RecyclerView.ViewHolder {
		ImageView picon;
		CardView card;
		TextView serviceName;
		Callback piconCallback;

		public ZapViewHolder(@NonNull View itemView) {
			super(itemView);
			picon = itemView.findViewById(R.id.picon);
			card = itemView.findViewById(R.id.textCard);
			serviceName = itemView.findViewById(android.R.id.text1);
			piconCallback = new Callback() {
				@Override
				public void onSuccess() {
					card.setVisibility(View.GONE);
					picon.setVisibility(View.VISIBLE);
				}

				@Override
				public void onError(Exception e) {
					Log.w(TAG, String.format("Error loading picon for %s", serviceName.getText()));
					card.setVisibility(View.VISIBLE);
					picon.setVisibility(View.GONE);
				}
			};
		}
	}
}
