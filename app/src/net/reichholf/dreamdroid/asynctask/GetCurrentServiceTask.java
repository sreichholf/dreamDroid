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
	protected Boolean doInBackground(Void... voids) {
		mCurrent = null;
		if (isCancelled()) {
			return false;
		}
		mCurrent = HttpFragmentHelper.fetchCurrentService(getHttpClient());
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetCurrentServiceTaskHandler handler = (GetCurrentServiceTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result);
		handler.onCurrentServiceReady(success, mCurrent, success ? null : getErrorText());
	}

	public interface GetCurrentServiceTaskHandler extends AsyncHttpTaskBaseHandler {
		void onCurrentServiceReady(boolean success, @Nullable CurrentService current, @Nullable String errorText);
	}
}
