/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.assist.FailReason;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;

/**
 * @author sre
 * 
 */
public class ZapListAdapter extends ArrayAdapter<ExtendedHashMap> {
	@SuppressWarnings("unused")
	private static String LOG_TAG = "ZapListAdapter";
	private ZapAnimateImageDisplayListener sZapAnimateDisplayListener = new ZapAnimateImageDisplayListener();

	private LayoutInflater mInflater;
	private int mLayoutId;

	/**
	 * @param context
	 * @param services
	 */
	public ZapListAdapter(Context context, int layoutId, ArrayList<ExtendedHashMap> services) {
		super(context, 0, services);
		DreamDroid.initImageLoader(getContext().getApplicationContext());
		mInflater = LayoutInflater.from(context);
		mLayoutId = layoutId;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		ExtendedHashMap service = getItem(position);


		ZapViewHolder viewHolder = null;
		if (view == null) {
			view = mInflater.inflate(mLayoutId, parent, false);
			viewHolder = new ZapViewHolder();
			viewHolder.picon = (ImageView) view.findViewById(R.id.picon);
			viewHolder.serviceName = (TextView) view.findViewById(android.R.id.text1);
			view.setTag(viewHolder);
		} else {
			viewHolder = (ZapViewHolder) view.getTag();
		}

		if (service != null) {
			viewHolder.serviceName.setVisibility(View.VISIBLE);
			viewHolder.serviceName.setText(service.getString(Service.KEY_NAME));
			Picon.setPiconForView(getContext(), viewHolder.picon, service, sZapAnimateDisplayListener);
		}

		return view;
	}

	static class ZapViewHolder {
		ImageView picon;
		TextView serviceName;
	}

	public class ZapAnimateImageDisplayListener extends Picon.AnimateImageDisplayListener {
		public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
			super.onLoadingComplete(imageUri, view, loadedImage);

			ZapViewHolder holder = (ZapViewHolder) ( (View) view.getParent() ).getTag();
			holder.serviceName.setVisibility(View.INVISIBLE);
		}
	}

}
