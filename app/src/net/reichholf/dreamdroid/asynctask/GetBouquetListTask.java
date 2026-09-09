package net.reichholf.dreamdroid.asynctask;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;

public class GetBouquetListTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	public class Bouquets {
		public ArrayList<Service> tv;
		public ArrayList<Service> radio;

		public Bouquets() {
			tv = new ArrayList<>();
			radio = new ArrayList<>();
		}
	}

	protected String mTV;
	protected String mRadio;

	private Bouquets mBouquets;

	public GetBouquetListTask(AsyncHttpTaskBaseHandler taskHandler) {
		super(taskHandler);
		GetBouquetListTaskHandler t = (GetBouquetListTaskHandler) mTaskHandler.get();
		mTV = t.getResources().getStringArray(R.array.servicerefstv)[0];
		mRadio = t.getResources().getStringArray(R.array.servicerefsradio)[0];
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mBouquets = new Bouquets();
		if (isCancelled())
			return false;

		addBouquets(mTV, mBouquets.tv);
		if (getHttpClient().hasError()) {
			return false;
		}
		addBouquets(mRadio, mBouquets.radio);
		return !getHttpClient().hasError();
	}

	private boolean addBouquets(String ref, ArrayList<Service> target) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", ref));
		if (isCancelled())
			return false;
		target.addAll(HttpFragmentHelper.fetchServices(getHttpClient(), params));
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetBouquetListTaskHandler taskHandler = (GetBouquetListTaskHandler) mTaskHandler.get();
		if (isInvalid(taskHandler))
			return;

		boolean success = Boolean.TRUE.equals(result);
		taskHandler.onBouquetListReady(success, mBouquets, success ? null : getErrorText());
	}

	public interface GetBouquetListTaskHandler extends AsyncHttpTaskBaseHandler {
		@NonNull
		Resources getResources();

		boolean isAdded();

		void onBouquetListReady(boolean result, Bouquets bouquets, String errorText);
	}
}