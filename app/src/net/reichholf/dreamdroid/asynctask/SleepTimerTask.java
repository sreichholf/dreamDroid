package net.reichholf.dreamdroid.asynctask;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SleepTimerRequestHandler;

import java.util.ArrayList;

/**
 * Created by Stephan on 27.12.2015.
 */
public class SleepTimerTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, Void, Boolean> {
	protected ExtendedHashMap mResult;
	protected SleepTimerRequestHandler mHandler;
	protected boolean mDialogOnFinish;
	public SleepTimerTask(boolean dialogOnFinish, SleepTimerTaskHandler taskHandler) {
		super(taskHandler);
		mHandler = new SleepTimerRequestHandler();
		mDialogOnFinish = dialogOnFinish;
	}

	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
		publishProgress();
		String xml = mHandler.get(getHttpClient(), params[0]);

		if (xml != null) {
			ExtendedHashMap result = new ExtendedHashMap();
			mHandler.parse(xml, result);

			String enabled = result.getString(SleepTimer.KEY_ENABLED);

			if (enabled != null) {
				mResult = result;
				return true;
			}
		}

		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		SleepTimerTaskHandler resultHandler = (SleepTimerTaskHandler) mTaskHandler.get();
		if (isCancelled() || resultHandler == null)
			return;

		if (!result || mResult == null)
			mResult = new ExtendedHashMap();

		resultHandler.onSleepTimerSet(result, mResult, mDialogOnFinish, getErrorText());
	}

	public interface SleepTimerTaskHandler extends AsyncHttpTaskBaseHandler {
		void onSleepTimerSet(boolean success, ExtendedHashMap result, boolean openDialog, String errorText);
	}
}