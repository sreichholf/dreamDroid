package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.text.format.Time;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Stephan on 09.03.14.
 */
public class MultiEpgFragment extends AbstractHttpFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.multiepg, null);
		LinearLayout content = (LinearLayout) v.findViewById(R.id.content);
		content.addView(createTimeLine(inflater));

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

		item.setClickable(!header);

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


}
