package net.reichholf.dreamdroid.asynctask;

import android.content.res.Resources;

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
	private ArrayList<ExtendedHashMap> mBouquetList;

	public GetBouquetListTask(AsyncHttpTaskBaseHandler taskHandler) {
		super(taskHandler);
	}

	@Override
	protected Boolean doInBackground(Void... unused) {
		mBouquetList = new ArrayList<>();
		GetBoquetListTaskHandler taskHandler = (GetBoquetListTaskHandler) mTaskHandler.get();
		if (isCancelled() || taskHandler == null)
			return false;

		AbstractListRequestHandler handler = new ServiceListRequestHandler();
		String ref = taskHandler.getResources().getStringArray(R.array.servicerefs)[0]; //Favorites TV;
		addBouquets(handler, ref);
		ref = taskHandler.getResources().getStringArray(R.array.servicerefs)[3]; // Favorites Radio
		addBouquets(handler, ref);

		return true;
	}

	private boolean addBouquets(AbstractListRequestHandler handler, String ref) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", ref));
		String xml = handler.getList(getHttpClient(), params);
		return xml != null && !isCancelled() && handler.parseList(xml, mBouquetList);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetBoquetListTaskHandler taskHandler = (GetBoquetListTaskHandler) mTaskHandler.get();
		if (isCancelled() || taskHandler == null)
			return;

		taskHandler.onBouquetListReady(result, mBouquetList, getErrorText());
	}

	public interface GetBoquetListTaskHandler extends AsyncHttpTaskBaseHandler {
		Resources getResources();

		void onBouquetListReady(boolean result, ArrayList<ExtendedHashMap> bouquetList, String errorText);
	}
}