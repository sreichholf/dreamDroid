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

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

/**
 * @author sre
 * 
 */
public class EpgDetailDialog extends ActionDialog {
	private ExtendedHashMap mCurrentItem;

	public EpgDetailDialog() {
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

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();
		mCurrentItem = new ExtendedHashMap((HashMap<String, Object>) args.get("currentItem"));
		boolean showNext = args.getBoolean("showNext", false);

		String prefix = "";
		if(showNext)
			prefix = Event.PREFIX_NEXT;

		String servicename = mCurrentItem.getString(prefix.concat(Event.KEY_SERVICE_NAME));
		String title = mCurrentItem.getString(prefix.concat(Event.KEY_EVENT_TITLE));
		String date = mCurrentItem.getString(prefix.concat(Event.KEY_EVENT_START_READABLE));

		MaterialDialog dialog = null;
		if (!"N/A".equals(title) && date != null) {
			date = date.concat(" (" + mCurrentItem.getString(Event.KEY_EVENT_DURATION_READABLE) + " "
					+ getText(R.string.minutes_short) + ")");
			String descShort = mCurrentItem.getString(Event.KEY_EVENT_DESCRIPTION, "");
			String descEx = mCurrentItem.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED);

			MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
			builder.title(title)
					.autoDismiss(true)
					.customView(R.layout.epg_item_dialog, false);

			dialog = builder.build();
			View view = dialog.getCustomView();

			TextView textServiceName = (TextView) view.findViewById(R.id.service_name);
			textServiceName.setText(servicename);

			TextView textShort = (TextView) view.findViewById(R.id.epg_short);
			if("".equals(descShort))
				textShort.setVisibility(View.GONE);
			else
				textShort.setText(descShort);
			TextView textTime = (TextView) view.findViewById(R.id.epg_time);
			textTime.setText(date);

			TextView textDescEx = (TextView) view.findViewById(R.id.epg_description_extended);
			textDescEx.setText(descEx);

			Button buttonSetTimer = (Button) view.findViewById(R.id.ButtonSetTimer);
			buttonSetTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_SET_TIMER, null);
				}
			});

			Button buttonEditTimer = (Button) view.findViewById(R.id.ButtonEditTimer);
			buttonEditTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_EDIT_TIMER, null);
				}
			});

			Button buttonIMDb = (Button) view.findViewById(R.id.ButtonImdb);
			buttonIMDb.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_IMDB, null);
				}
			});

			Button buttonSimilar = (Button) view.findViewById(R.id.ButtonSimilar);
			buttonSimilar.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_FIND_SIMILAR, null);
				}
			});
		} else {
			MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
			builder.title(R.string.not_available)
					.autoDismiss(true)
					.positiveText(R.string.close)
					.content(R.string.no_epg_available);

			dialog = builder.build();
		}
		return dialog;
	}
}
