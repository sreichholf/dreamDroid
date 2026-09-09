package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.CurrentService;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;

public class GetCurrentServiceTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	@Nullable
	private CurrentService mCurrent;

	public GetCurrentServiceTask(GetCurrentServiceTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mCurrent = null;
		if (isCancelled()) {
			return false;
		}
		mCurrent = HttpFragmentHelper.fetchCurrentService(getHttpClient());
		if (getHttpClient().hasError()) {
			return false;
		}
		// HTTP ok but SAX/parser failed — not a successful load.
		return mCurrent != null;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetCurrentServiceTaskHandler handler = (GetCurrentServiceTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result) && mCurrent != null;
		String errorText = null;
		if (!success) {
			if (getHttpClient().hasError()) {
				errorText = getErrorText();
			} else {
				errorText = handler.getString(net.reichholf.dreamdroid.R.string.error_parsing);
			}
		}
		handler.onCurrentServiceReady(success, mCurrent, errorText);
	}

	public interface GetCurrentServiceTaskHandler extends AsyncHttpTaskBaseHandler {
		void onCurrentServiceReady(boolean success, @Nullable CurrentService current, @Nullable String errorText);
	}
}
