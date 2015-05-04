/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.adapter.recyclerview;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;

import java.util.ArrayList;

/**
 * @author sre
 * 
 */
public class TimerAdapter extends BaseAdapter<TimerAdapter.TimerViewHolder> {
	private CharSequence[] mState;
	private CharSequence[] mAction;
	private int[] mStateColor;

	/**
	 * @param context
	 * @param data
	 */
	public TimerAdapter(Context context, ArrayList<ExtendedHashMap> data) {
		super(data);
		mState = context.getResources().getTextArray(R.array.timer_state);
		mAction = context.getResources().getTextArray(R.array.timer_action);
		mStateColor = context.getResources().getIntArray(R.array.timer_state_color);
	}

	@Override
	public TimerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.timer_list_item, parent, false);
		return new TimerViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(TimerViewHolder holder, int position) {
		ExtendedHashMap timer = mData.get(position);
		if (timer != null) {
			holder.name.setText(timer.getString(Timer.KEY_NAME));
			holder.service.setText(timer.getString(Timer.KEY_SERVICE_NAME));
			holder.begin.setText(timer.getString(Timer.KEY_BEGIN_READEABLE));
			holder.end.setText(timer.getString(Timer.KEY_END_READABLE));

			int actionId = 0;

			try {
				actionId = Integer.parseInt(timer.getString(Timer.KEY_JUST_PLAY));
			} catch (Exception e) {
				Log.e(DreamDroid.LOG_TAG, "[TimerListAdapter] Error getting timer action: " + e.getMessage());
			}

			holder.action.setText(mAction[actionId]);

			int stateId = Integer.parseInt(timer.getString(Timer.KEY_STATE));
			int disabled = Integer.parseInt(timer.getString(Timer.KEY_DISABLED));
			// The state for disabled timers is 3
			// If any timer is disabled we add 1 to the state get the disabled
			// color/text
			stateId += disabled;
			holder.state.setText(mState[stateId]);
			holder.stateIndicator.setBackgroundColor(mStateColor[stateId]);
		}
	}

	public class TimerViewHolder extends RecyclerView.ViewHolder {
		public TextView name;
		public TextView service;
		public TextView begin;
		public TextView end;
		public TextView action;
		public TextView state;
		public TextView stateIndicator;

		public TimerViewHolder(View itemView){
			super(itemView);
			name = (TextView) itemView.findViewById(R.id.timer_name);
			service = (TextView) itemView.findViewById(R.id.service_name);
			begin = (TextView) itemView.findViewById(R.id.timer_start);
			end = (TextView) itemView.findViewById(R.id.timer_end);
			action = (TextView) itemView.findViewById(R.id.timer_action);
			state = (TextView) itemView.findViewById(R.id.timer_state);
			stateIndicator = (TextView) itemView.findViewById(R.id.timer_state_indicator);
		}
	}
}
