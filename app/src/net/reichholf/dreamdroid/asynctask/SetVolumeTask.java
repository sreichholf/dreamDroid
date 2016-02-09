package net.reichholf.dreamdroid.asynctask;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler;

import java.util.ArrayList;

public class SetVolumeTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, Void, Boolean> {
	private ExtendedHashMap mVolume;
	private VolumeRequestHandler mHandler;
	public SetVolumeTask(AsyncHttpTaskBaseHandler taskHandler) {
		super(taskHandler);
	}

	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
		if (isCancelled())
			return false;
		publishProgress();
		mHandler = new VolumeRequestHandler();
		String xml = mHandler.get(getHttpClient(), params[0]);

		if (xml != null) {
			ExtendedHashMap volume = new ExtendedHashMap();
			mHandler.parse(xml, volume);

			String current = volume.getString(Volume.KEY_CURRENT);
			if (current != null) {
				mVolume = volume;
				return true;
			}
		}

		return false;
	}

	protected void onPostExecute(Boolean result) {
		SetVolumeTaskHandler taskHandler = (SetVolumeTaskHandler) mTaskHandler.get();
		if (isCancelled() || taskHandler == null)
			return;
		if (!result || mVolume == null)
			mVolume = new ExtendedHashMap();
		taskHandler.onVolumeSet(result, mVolume);
	}

	public interface SetVolumeTaskHandler {
		void onVolumeSet(boolean result, ExtendedHashMap volume);
	}
}