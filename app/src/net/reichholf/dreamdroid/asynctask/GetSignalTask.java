package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Signal;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;

public class GetSignalTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	@Nullable
	private Signal mSignal;

	public GetSignalTask(GetSignalTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mSignal = null;
		if (isCancelled()) {
			return false;
		}
		mSignal = HttpFragmentHelper.fetchSignal(getHttpClient());
		if (getHttpClient().hasError()) {
			return false;
		}
		return mSignal != null && !mSignal.isEmpty();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetSignalTaskHandler handler = (GetSignalTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result) && mSignal != null;
		String errorText = null;
		if (!success) {
			if (getHttpClient().hasError()) {
				errorText = getErrorText();
			} else {
				errorText = handler.getString(net.reichholf.dreamdroid.R.string.error_parsing);
			}
		}
		handler.onSignalReady(success, mSignal, errorText);
	}

	public interface GetSignalTaskHandler extends AsyncHttpTaskBaseHandler {
		void onSignalReady(boolean success, @Nullable Signal signal, @Nullable String errorText);
	}
}
