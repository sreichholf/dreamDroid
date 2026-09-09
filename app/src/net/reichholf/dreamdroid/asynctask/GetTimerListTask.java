package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Timer;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;

import java.util.ArrayList;
import java.util.List;

public class GetTimerListTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	private ArrayList<Timer> mTimers;

	public GetTimerListTask(GetTimerListTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mTimers = new ArrayList<>();
		if (isCancelled()) {
			return false;
		}
		mTimers.addAll(HttpFragmentHelper.fetchTimers(getHttpClient()));
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetTimerListTaskHandler handler = (GetTimerListTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result);
		handler.onTimerListReady(success, mTimers, success ? null : getErrorText());
	}

	public interface GetTimerListTaskHandler extends AsyncHttpTaskBaseHandler {
		void onTimerListReady(boolean success, @NonNull List<Timer> timers, @Nullable String errorText);
	}
}
