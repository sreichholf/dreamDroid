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
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

/**
 * @author sre
 * 
 */
public class EpgDetailDialog extends BottomSheetActionDialog {
	private ExtendedHashMap mCurrentItem;
	private BottomSheetBehavior mBottomSheetBehavior;

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
		final Dialog dialog;

		Bundle args = getArguments();
		mCurrentItem = new ExtendedHashMap((HashMap<String, Object>) args.get("currentItem"));
		final boolean isNext = args.getBoolean("showNext", false);

		String prefix = "";
		if(isNext)
			prefix = Event.PREFIX_NEXT;

		String servicename = mCurrentItem.getString(Event.KEY_SERVICE_NAME);
		String title = mCurrentItem.getString(prefix + Event.KEY_EVENT_TITLE);
		String date = mCurrentItem.getString(prefix + Event.KEY_EVENT_START_READABLE);

		if (!"N/A".equals(title) && date != null) {
			dialog = super.onCreateDialog(savedInstanceState);
			date = date.concat(" (" + mCurrentItem.getString(prefix + Event.KEY_EVENT_DURATION_READABLE) + " "
					+ getText(R.string.minutes_short) + ")");
			String descShort = mCurrentItem.getString(prefix + Event.KEY_EVENT_DESCRIPTION, "");
			String descEx = mCurrentItem.getString(prefix + Event.KEY_EVENT_DESCRIPTION_EXTENDED);

			View view = View.inflate(getContext(), R.layout.epg_item_dialog, null);

			Toolbar tb = (Toolbar) view.findViewById(R.id.toolbar_epg_detail);
			tb.setTitle(title);

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
					finishDialog(Statics.ACTION_SET_TIMER, isNext);
				}
			});

			Button buttonEditTimer = (Button) view.findViewById(R.id.ButtonEditTimer);
			buttonEditTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_EDIT_TIMER, isNext);
				}
			});

			Button buttonIMDb = (Button) view.findViewById(R.id.ButtonImdb);
			buttonIMDb.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_IMDB, isNext);
				}
			});

			Button buttonSimilar = (Button) view.findViewById(R.id.ButtonSimilar);
			buttonSimilar.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishDialog(Statics.ACTION_FIND_SIMILAR, isNext);
				}
			});
			dialog.setContentView(view);

			mBottomSheetBehavior = BottomSheetBehavior.from(((View) view.getParent()));
			if (mBottomSheetBehavior != null) {
				mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
			}
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
