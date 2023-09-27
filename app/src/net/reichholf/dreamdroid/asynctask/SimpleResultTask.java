package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;

import java.util.ArrayList;

public class SimpleResultTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, String, Boolean> {
	protected ExtendedHashMap mResult;
	protected SimpleResultRequestHandler mHandler;
	public SimpleResultTask(SimpleResultRequestHandler handler, SimpleResultTaskHandler taskHandler) {
		super(taskHandler);
		mHandler = handler;
	}

	@NonNull
	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair> params) {
		if (isCancelled())
			return false;
		String xml = mHandler.get(getHttpClient(), params);

		if (xml != null) {
			ExtendedHashMap result = mHandler.parseSimpleResult(xml);

			String stateText = result.getString("statetext");

			if (stateText != null) {
				mResult = result;
				return true;
			}
		}

		return false;
	}

	protected void onPostExecute(Boolean result) {
		SimpleResultTaskHandler resultHandler = (SimpleResultTaskHandler) mTaskHandler.get();
		if (isInvalid(resultHandler))
			return;
		if (!result || mResult == null)
			mResult = new ExtendedHashMap();

		resultHandler.onSimpleResult(result, mResult);
	}

	public interface SimpleResultTaskHandler extends AsyncHttpTaskBaseHandler {
		void onSimpleResult(boolean success, ExtendedHashMap result);
	}
}