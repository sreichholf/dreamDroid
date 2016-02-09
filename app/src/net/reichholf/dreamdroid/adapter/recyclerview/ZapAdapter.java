package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stephan on 03.02.2016.
 */
public class ZapAdapter extends BaseAdapter<ZapAdapter.ZapViewHolder> {
	private ZapAnimateImageDisplayListener sZapAnimateDisplayListener = new ZapAnimateImageDisplayListener();
	private Context mContext;

	public ZapAdapter(Context context, ArrayList<ExtendedHashMap> data) {
		super(data);
		mContext = context;
	}

	@Override
	public ZapViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.zap_grid_item, parent, false);
		ZapViewHolder zvh = new ZapViewHolder(itemView);
		itemView.setTag(zvh);
		return zvh;
	}

	@Override
	public void onBindViewHolder(ZapViewHolder holder, int position) {
		ExtendedHashMap service = mData.get(position);
		if (service != null) {
			holder.serviceName.setVisibility(View.VISIBLE);
			holder.serviceName.setText(service.getString(Service.KEY_NAME));
			Picon.setPiconForView(mContext, holder.picon, service, sZapAnimateDisplayListener);
		}
	}

	static class ZapViewHolder extends RecyclerView.ViewHolder {
		ImageView picon;
		TextView serviceName;

		public ZapViewHolder(View itemView) {
			super(itemView);
			picon = (ImageView) itemView.findViewById(R.id.picon);
			serviceName = (TextView) itemView.findViewById(android.R.id.text1);
		}
	}

	public class ZapAnimateImageDisplayListener extends Picon.AnimateImageDisplayListener {
		public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
			super.onLoadingComplete(imageUri, view, loadedImage);

			ZapViewHolder holder = (ZapViewHolder) ((View) view.getParent()).getTag();
			if(holder != null)
				holder.serviceName.setVisibility(View.INVISIBLE);
		}
	}
}
