package net.reichholf.dreamdroid.adapter.recyclerview;

import android.widget.ImageView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;

import java.util.ArrayList;

/**
 * Created by Stephan on 14.05.2015.
 */
public class EpgAdapter extends SimpleTextAdapter {
	public EpgAdapter(ArrayList<ExtendedHashMap> data, int layoutId, String[] keys, int[] ids) {
		super(data, layoutId, keys, ids);
	}

	@Override
	public void onBindViewHolder(SimpleViewHolder holder, int position) {
		super.onBindViewHolder(holder, position);
		ImageView picon = (ImageView) holder.itemView.findViewById(R.id.picon);
		ExtendedHashMap service = mData.get(position);
		Picon.setPiconForView(holder.itemView.getContext(), picon, service, Statics.TAG_PICON);
	}
}
