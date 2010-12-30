/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * @author sre
 *
 */
public class TimerListAdapter extends ArrayAdapter<ExtendedHashMap> {
	
	private ArrayList<ExtendedHashMap> mItems;
	private CharSequence[] mState;
	private CharSequence[] mAction;
	private int[] mStateColor;
	
	/**
	 * @param context
	 * @param textViewResourceId
	 * @param objects
	 */
	public TimerListAdapter(Context context, int textViewResourceId, ArrayList<ExtendedHashMap> items) {
		super(context, textViewResourceId, items);
		mItems = items;
		mState = getContext().getResources().getTextArray(R.array.timer_state);
		mAction = getContext().getResources().getTextArray(R.array.timer_action);
		mStateColor = getContext().getResources().getIntArray(R.array.timer_state_color);		
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if(view == null){
			LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = li.inflate(R.layout.timer_list_item, null);
		}
		
		ExtendedHashMap timer = mItems.get(position);
		if(timer != null){			
			TextView timerName = (TextView) view.findViewById(R.id.timer_name);
			TextView serviceName = (TextView) view.findViewById(R.id.service_name);
			TextView begin = (TextView) view.findViewById(R.id.timer_start);
			TextView end = (TextView) view.findViewById(R.id.timer_end);
			TextView action = (TextView) view.findViewById(R.id.timer_action);
			TextView state = (TextView) view.findViewById(R.id.timer_state);
			TextView stateIndicator = (TextView) view.findViewById(R.id.timer_state_indicator);
			
			timerName.setText(timer.getString(Timer.NAME));
			serviceName.setText(timer.getString(Timer.SERVICE_NAME));
			begin.setText(timer.getString(Timer.BEGIN_READEABLE));
			end.setText(timer.getString(Timer.END_READABLE));
			
			int actionId = 0;
			
			try{			
				actionId = Integer.parseInt(timer.getString(Timer.JUST_PLAY));
			} catch (Exception e){
				Log.e(DreamDroid.LOG_TAG, "[TimerListAdapter] Error getting timer action: " + e.getMessage());
			}
			
			action.setText(mAction[actionId]);
			
			int stateId = Integer.parseInt(timer.getString(Timer.STATE));
			int disabled = Integer.parseInt(timer.getString(Timer.DISABLED));
			//The state for disabled timers is 3 
			//If any timer is disabled we add 1 to the state get the disabled color/text
			stateId += disabled;
			state.setText(mState[stateId]);
			stateIndicator.setBackgroundColor(mStateColor[stateId]);			
		}
		
		
		return view;
	}
	
	

}
