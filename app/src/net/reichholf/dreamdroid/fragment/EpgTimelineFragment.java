package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.view.EnhancedHorizontalScrollView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Stephan on 09.03.14.
 */
public class EpgTimelineFragment extends BaseHttpRecyclerEventFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.multiepg, null);

		final EnhancedHorizontalScrollView headerScroll = (EnhancedHorizontalScrollView) v.findViewById(R.id.scrollview_header);
		final EnhancedHorizontalScrollView contentScroll = (EnhancedHorizontalScrollView) v.findViewById(R.id.scrollview_content);
		headerScroll.addScrollChangedListener(new EnhancedHorizontalScrollView.OnScrollChangedListener() {
			@Override
			public void onScrollChanged(int x, int y) {
				contentScroll.scrollTo(x, y);
			}
		});
		contentScroll.addScrollChangedListener(new EnhancedHorizontalScrollView.OnScrollChangedListener() {
			@Override
			public void onScrollChanged(int x, int y) {
				headerScroll.scrollTo(x, y);
			}
		});

		LinearLayout header = (LinearLayout) v.findViewById(R.id.header);
		header.addView(createTimeLine(inflater));
		LinearLayout content = (LinearLayout) v.findViewById(R.id.content);
		for (int i = 0; i < 48; ++i) {
			LinearLayout row = (LinearLayout) inflater.inflate(R.layout.multiepg_row, null);
			for (int j = 0; j < 10; ++j) {
				int width = new Double((Math.random() * 120)).intValue() * getScaleFactor();
				View item = createRowItem(inflater, width, String.format("%spx", width), false);
				row.addView(item);
			}
			content.addView(row);
		}
		return v;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		reload();
	}

	@Override
	public boolean hasHeader() {
		return true;
	}

	public LinearLayout createTimeLine(LayoutInflater inflater) {
		LinearLayout row = (LinearLayout) inflater.inflate(R.layout.multiepg_row, null);

		Date now = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);

		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int width = cal.get(Calendar.MINUTE) * getScaleFactor();
		View v = createRowItem(inflater, width, String.valueOf(hour), true);
		row.addView(v);
		width = 60 * getScaleFactor();
		for (int i = 0; i < 24; ++i) {
			cal.add(Calendar.HOUR_OF_DAY, 1);
			hour = cal.get(Calendar.HOUR_OF_DAY);
			v = createRowItem(inflater, width, String.valueOf(hour), true);
			v.setClickable(false);
			row.addView(v);
		}
		return row;
	}

	private View createRowItem(LayoutInflater inflater, int width, String text1, boolean header) {
		View item;
		if (header)
			item = inflater.inflate(R.layout.multiepg_header_item, null);
		else
			item = inflater.inflate(R.layout.multiepg_row_item, null);

		TextView tv = (TextView) item.findViewById(android.R.id.text1);
		tv.setText(text1);
		ViewGroup.LayoutParams params = tv.getLayoutParams();
		params.width = width;
		tv.setLayoutParams(params);

		return item;
	}

	private int getScaleFactor() {
		return new Double(3 * getResources().getDimension(R.dimen.single_dp)).intValue();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}


	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("bRef", "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"));
		return params;
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		return new AsyncListLoader(getAppCompatActivity(), new EventListRequestHandler(
				URIStore.EPG_MULTI), false, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {
		super.onLoadFinished(loader, result);
	}
}
