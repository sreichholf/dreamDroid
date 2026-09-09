package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.DeviceInfo;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;

public class GetDeviceInfoTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	@Nullable
	private DeviceInfo mInfo;

	public GetDeviceInfoTask(GetDeviceInfoTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mInfo = null;
		if (isCancelled()) {
			return false;
		}
		mInfo = HttpFragmentHelper.fetchDeviceInfo(getHttpClient());
		if (getHttpClient().hasError()) {
			return false;
		}
		return mInfo != null;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetDeviceInfoTaskHandler handler = (GetDeviceInfoTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result) && mInfo != null;
		String errorText = null;
		if (!success) {
			if (getHttpClient().hasError()) {
				errorText = getErrorText();
			} else {
				errorText = handler.getString(net.reichholf.dreamdroid.R.string.error_parsing);
			}
		}
		handler.onDeviceInfoReady(success, mInfo, errorText);
	}

	public interface GetDeviceInfoTaskHandler extends AsyncHttpTaskBaseHandler {
		void onDeviceInfoReady(boolean success, @Nullable DeviceInfo info, @Nullable String errorText);
	}
}
