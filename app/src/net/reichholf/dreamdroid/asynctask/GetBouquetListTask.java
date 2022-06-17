package net.reichholf.dreamdroid.asynctask;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;

import java.util.ArrayList;

/**
 * @author sreichholf Fetches a service list async. Does all the
 *         error-handling, refreshing and title-setting
 */
public class GetBouquetListTask extends AsyncHttpTaskBase<Void, String, Boolean> {
	public class Bouquets {
		public ArrayList<ExtendedHashMap> tv;
		public ArrayList<ExtendedHashMap> radio;

		public Bouquets() {
			tv = new ArrayList<>();
			radio = new ArrayList<>();
		}
	}

	private Bouquets mBouquets;

	public GetBouquetListTask(AsyncHttpTaskBaseHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(Void... unused) {
		mBouquets = new Bouquets();
		GetBoquetListTaskHandler taskHandler = (GetBoquetListTaskHandler) mTaskHandler.get();
		if (isCancelled() || taskHandler == null)
			return false;

		if (!taskHandler.isAdded())
			return false;

		AbstractListRequestHandler handler = new ServiceListRequestHandler();
		String ref = taskHandler.getResources().getStringArray(R.array.servicerefs)[0]; //Favorites TV;
		addBouquets(handler, ref, mBouquets.tv);
		ref = taskHandler.getResources().getStringArray(R.array.servicerefs)[3]; // Favorites Radio
		addBouquets(handler, ref, mBouquets.radio);

		return true;
	}

	private boolean addBouquets(@NonNull AbstractListRequestHandler handler, String ref, ArrayList<ExtendedHashMap> target) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", ref));
		String xml = handler.getList(getHttpClient(), params);
		return xml != null && !isCancelled() && handler.parseList(xml, target);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetBoquetListTaskHandler taskHandler = (GetBoquetListTaskHandler) mTaskHandler.get();
		if (isCancelled() || taskHandler == null)
			return;

		taskHandler.onBouquetListReady(result, mBouquets, getErrorText());
	}

	public interface GetBoquetListTaskHandler extends AsyncHttpTaskBaseHandler {
		@NonNull
		Resources getResources();

		boolean isAdded();

		void onBouquetListReady(boolean result, Bouquets bouquets, String errorText);
	}
}