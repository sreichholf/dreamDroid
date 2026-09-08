package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;
import java.util.List;

public class GetServiceListTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, String, Boolean> {
	private ArrayList<Service> mServices;

	public GetServiceListTask(GetServiceListTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair> params) {
		mServices = new ArrayList<>();
		if (isCancelled()) {
			return false;
		}
		List<NameValuePair> requestParams = params != null ? params : new ArrayList<>();
		mServices.addAll(HttpFragmentHelper.fetchServices(getHttpClient(), requestParams));
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetServiceListTaskHandler handler = (GetServiceListTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result);
		handler.onServiceListReady(success, mServices, success ? null : getErrorText());
	}

	public interface GetServiceListTaskHandler extends AsyncHttpTaskBaseHandler {
		void onServiceListReady(boolean success, @NonNull List<Service> services, @Nullable String errorText);
	}
}
