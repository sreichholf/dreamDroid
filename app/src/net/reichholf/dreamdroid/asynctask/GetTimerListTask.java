package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Timer;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetTimerListTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	@Nullable
	private ArrayList<Timer> mTimers;

	public GetTimerListTask(GetTimerListTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mTimers = null;
		if (isCancelled()) {
			return false;
		}
		List<Timer> fetched = HttpFragmentHelper.fetchTimers(getHttpClient());
		if (getHttpClient().hasError()) {
			mTimers = new ArrayList<>();
			return false;
		}
		// Successful HTTP can still fail SAX; null means parse failure (not an empty timer list).
		if (fetched == null) {
			mTimers = new ArrayList<>();
			return false;
		}
		mTimers = new ArrayList<>(fetched);
		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetTimerListTaskHandler handler = (GetTimerListTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result) && mTimers != null;
		List<Timer> timers = mTimers != null ? mTimers : Collections.emptyList();
		String errorText = null;
		if (!success) {
			if (getHttpClient().hasError()) {
				errorText = getErrorText();
			} else {
				errorText = handler.getString(net.reichholf.dreamdroid.R.string.error_parsing);
			}
		}
		handler.onTimerListReady(success, timers, errorText);
	}

	public interface GetTimerListTaskHandler extends AsyncHttpTaskBaseHandler {
		void onTimerListReady(boolean success, @NonNull List<Timer> timers, @Nullable String errorText);
	}
}
