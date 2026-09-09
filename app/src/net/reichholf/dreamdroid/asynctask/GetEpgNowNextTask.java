package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.enigma.ServiceNowNext;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import java.util.ArrayList;
import java.util.List;

public class GetEpgNowNextTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, String, Boolean> {
	private ArrayList<ServiceNowNext> mRows;

	public GetEpgNowNextTask(GetEpgNowNextTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair> params) {
		mRows = new ArrayList<>();
		if (isCancelled()) {
			return false;
		}
		List<NameValuePair> requestParams = params != null ? params : new ArrayList<>();
		String uri = DreamDroid.featureNowNext() ? URIStore.EPG_NOWNEXT : URIStore.EPG_NOW;
		mRows.addAll(HttpFragmentHelper.fetchEpgNowNext(getHttpClient(), requestParams, uri));
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetEpgNowNextTaskHandler handler = (GetEpgNowNextTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result);
		handler.onEpgNowNextReady(success, mRows, success ? null : getErrorText());
	}

	public interface GetEpgNowNextTaskHandler extends AsyncHttpTaskBaseHandler {
		void onEpgNowNextReady(boolean success, @NonNull List<ServiceNowNext> rows, @Nullable String errorText);
	}
}
