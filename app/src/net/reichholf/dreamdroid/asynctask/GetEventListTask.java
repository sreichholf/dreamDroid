package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import java.util.ArrayList;
import java.util.List;

public class GetEventListTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, String, Boolean> {
	private final String mUri;
	private ArrayList<Event> mEvents;

	public GetEventListTask(GetEventListTaskHandler taskHandler) {
		this(taskHandler, URIStore.EPG_SERVICE);
	}

	public GetEventListTask(GetEventListTaskHandler taskHandler, @NonNull String uri) {
		super(taskHandler);
		mUri = uri;
	}

	@NonNull
	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair> params) {
		mEvents = new ArrayList<>();
		if (isCancelled()) {
			return false;
		}
		List<NameValuePair> requestParams = params != null ? params : new ArrayList<>();
		mEvents.addAll(HttpFragmentHelper.fetchEvents(getHttpClient(), requestParams, mUri));
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetEventListTaskHandler handler = (GetEventListTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result);
		handler.onEventListReady(success, mEvents, success ? null : getErrorText());
	}

	public interface GetEventListTaskHandler extends AsyncHttpTaskBaseHandler {
		void onEventListReady(boolean success, @NonNull List<Event> events, @Nullable String errorText);
	}
}
