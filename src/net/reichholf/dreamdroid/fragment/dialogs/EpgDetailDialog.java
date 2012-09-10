/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import java.util.HashMap;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * @author sre
 * 
 */
public class EpgDetailDialog extends PrimitiveDialog {
	private ExtendedHashMap mCurrentItem;

	public EpgDetailDialog() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onDestroyView() {
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}

	@SuppressWarnings("unchecked")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mCurrentItem = new ExtendedHashMap((HashMap<String, Object>) getArguments().get("currentItem"));

		View view = null;
		String servicename = mCurrentItem.getString(Event.KEY_SERVICE_NAME);
		String title = mCurrentItem.getString(Event.KEY_EVENT_TITLE);
		String date = mCurrentItem.getString(Event.KEY_EVENT_START_READABLE);

		if (!"N/A".equals(title) && date != null) {
			date = date.concat(" (" + (String) mCurrentItem.getString(Event.KEY_EVENT_DURATION_READABLE) + " "
					+ getText(R.string.minutes_short) + ")");
			String descEx = mCurrentItem.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED);

			view = inflater.inflate(R.layout.epg_item_dialog, container);
			getDialog().setTitle(title);

			TextView textServiceName = (TextView) view.findViewById(R.id.service_name);
			textServiceName.setText(servicename);

			TextView textTime = (TextView) view.findViewById(R.id.epg_time);
			textTime.setText(date);

			TextView textDescEx = (TextView) view.findViewById(R.id.epg_description_extended);
			textDescEx.setText(descEx);

			Button buttonSetTimer = (Button) view.findViewById(R.id.ButtonSetTimer);
			buttonSetTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_SET_TIMER);
				}
			});

			Button buttonEditTimer = (Button) view.findViewById(R.id.ButtonEditTimer);
			buttonEditTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_EDIT_TIMER);
				}
			});

			Button buttonIMDb = (Button) view.findViewById(R.id.ButtonImdb);
			buttonIMDb.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_IMDB);
				}
			});

			Button buttonSimilar = (Button) view.findViewById(R.id.ButtonSimilar);
			buttonSimilar.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_FIND_SIMILAR);
				}
			});
		} else {
			view = inflater.inflate(R.layout.simple_dialog_info, container);
			TextView text = (TextView) view.findViewById(R.id.text);
			getDialog().setTitle(R.string.not_available);
			text.setText(R.string.no_epg_available);
		}
		return view;
	}
}
