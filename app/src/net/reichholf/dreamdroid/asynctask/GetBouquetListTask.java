package net.reichholf.dreamdroid.asynctask;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;

/**
 * @author sreichholf Fetches a service list async. Does all the
 *         error-handling, refreshing and title-setting
 */
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
		mTV = t.getResources().getStringArray(R.array.servicerefs)[0]; //Favorites TV;
		mRadio = t.getResources().getStringArray(R.array.servicerefs)[3]; // Favorites Radio
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void unused) {
		mBouquets = new Bouquets();
		if (isCancelled())
			return false;

		addBouquets(mTV, mBouquets.tv);
		addBouquets(mRadio, mBouquets.radio);

		return true;
	}

	private boolean addBouquets(String ref, ArrayList<Service> target) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", ref));
		if (isCancelled())
			return false;
		target.addAll(HttpFragmentHelper.fetchServices(getHttpClient(), params));
		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetBouquetListTaskHandler taskHandler = (GetBouquetListTaskHandler) mTaskHandler.get();
		if (isInvalid(taskHandler))
			return;

		taskHandler.onBouquetListReady(result, mBouquets, getErrorText());
	}

	public interface GetBouquetListTaskHandler extends AsyncHttpTaskBaseHandler {
		@NonNull
		Resources getResources();

		boolean isAdded();

		void onBouquetListReady(boolean result, Bouquets bouquets, String errorText);
	}
}