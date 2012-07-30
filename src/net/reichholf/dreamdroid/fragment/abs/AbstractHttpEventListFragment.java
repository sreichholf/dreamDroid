/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.EpgSearchFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractHttpEventListFragment extends AbstractHttpListFragment {	
	protected String mReference;
	protected String mName;

	protected ProgressDialog mProgress;
	protected ExtendedHashMap mCurrentItem;
	protected MultiPaneHandler mMultiPaneHandler;
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		if(savedInstanceState != null){
			mReference = savedInstanceState.getString("reference");
			mName = savedInstanceState.getString("name");
			mCurrentItem = (ExtendedHashMap) savedInstanceState.getSerializable("currentItem");
		}
		mMultiPaneHandler = (MultiPaneHandler) getSherlockActivity();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("reference", mReference);
		outState.putString("name", mName);
		outState.putSerializable("currentItem", mCurrentItem);
		
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentItem = mMapList.get((int) id);
		// if the dialog has been opened before, remove that instance
		getSherlockActivity().removeDialog(Statics.DIALOG_EPG_ITEM_ID);
		getSherlockActivity().showDialog(Statics.DIALOG_EPG_ITEM_ID);
	}
	
	@Override
	public Dialog onCreateDialog(int id) {
		final Dialog dialog;
		
		if(mCurrentItem != null){
		
			switch (id) {
			case Statics.DIALOG_EPG_ITEM_ID:
	
				String servicename = mCurrentItem.getString(Event.KEY_SERVICE_NAME);
				String title = mCurrentItem.getString(Event.KEY_EVENT_TITLE);
				String date = mCurrentItem.getString(Event.KEY_EVENT_START_READABLE);
				if (!"N/A".equals(title) && date != null) {
					date = date.concat(" (" + (String) mCurrentItem.getString(Event.KEY_EVENT_DURATION_READABLE) + " "
							+ getText(R.string.minutes_short) + ")");
					String descEx = mCurrentItem.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED);
	
					dialog = new Dialog(getSherlockActivity());
					dialog.setContentView(R.layout.epg_item_dialog);
					dialog.setTitle(title);
	
					TextView textServiceName = (TextView) dialog.findViewById(R.id.service_name);
					textServiceName.setText(servicename);
	
					TextView textTime = (TextView) dialog.findViewById(R.id.epg_time);
					textTime.setText(date);
	
					TextView textDescEx = (TextView) dialog.findViewById(R.id.epg_description_extended);
					textDescEx.setText(descEx);
	
					Button buttonSetTimer = (Button) dialog.findViewById(R.id.ButtonSetTimer);
					buttonSetTimer.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							setTimerById(mCurrentItem);
							dialog.dismiss();
						}
					});
	
					Button buttonEditTimer = (Button) dialog.findViewById(R.id.ButtonEditTimer);
					buttonEditTimer.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							setTimerByEventData(mCurrentItem);
							dialog.dismiss();
						}
					});
					
					Button buttonIMDb = (Button) dialog.findViewById(R.id.ButtonImdb);
					buttonIMDb.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							IntentFactory.queryIMDb(getSherlockActivity(), mCurrentItem);
							dialog.dismiss();
						}
					});
					
					Button buttonSimilar = (Button) dialog.findViewById(R.id.ButtonSimilar);
					buttonSimilar.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							findSimilarEvents(mCurrentItem);
							dialog.dismiss();
						}
					});
				} else {
					// No EPG Information is available!
					AlertDialog.Builder builder = new AlertDialog.Builder(getSherlockActivity());
					builder.setMessage(R.string.no_epg_available).setCancelable(true)
							.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									dialog.cancel();
								}
							});
					dialog = builder.create();
				}
				break;
			default:
				dialog = null;
//				dialog = super.onCreateDialog(id);
			}
		} else {
			dialog = null;
			showToast(getString(R.string.error));
		}
		return dialog;
	}
	
	/**
	 * @param event
	 */
	protected void setTimerById(ExtendedHashMap event) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}
	
	/**
	 * @param event
	 */
	protected void findSimilarEvents(ExtendedHashMap event){
		//TODO fix findSimilarEvents
		EpgSearchFragment f = new EpgSearchFragment();
		Bundle args = new Bundle();
		args.putString(SearchManager.QUERY, event.getString(Event.KEY_EVENT_TITLE));
		
		f.setArguments(args);
		mMultiPaneHandler.showDetails(f);
	}

	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		super.onSimpleResult(success, result);
	}

	/**
	 * @param event
	 */
	protected void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent(mMultiPaneHandler, event, this);
	}
}
