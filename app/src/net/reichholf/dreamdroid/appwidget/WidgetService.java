package net.reichholf.dreamdroid.appwidget;

import android.content.Intent;
import androidx.annotation.NonNull;
import android.util.Log;

import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler;
import net.reichholf.dreamdroid.service.HttpIntentService;

import java.util.ArrayList;

/**
 * Created by Stephan on 08.12.13.
 */
public class WidgetService extends HttpIntentService {
	public static String TAG = WidgetService.class.getSimpleName();

	public static final int JOB_ID = 1337;

	public static final String KEY_KEYID = "key_id";
	public static final String KEY_WIDGETID = "widget_id";

	public static final String ACTION_ZAP = "net.reichholf.dreamdroid.appwidget.WidgetService.ACTION_ZAP";
	public static final String ACTION_RCU = "net.reichholf.dreamdroid.appwidget.WidgetService.ACTION_RCU";

	@Override
	public void onHandleWork(@NonNull Intent intent) {
		String action = intent.getAction();
		if(ACTION_RCU.equals(action))
			doRemoteRequest(intent);
		else if(ACTION_ZAP.equals(action))
			doZapRequest();
	}

	private void doRemoteRequest(Intent intent) {
		setupSSL();

		Profile profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(getApplicationContext(), intent.getIntExtra(KEY_WIDGETID, -1));

		SimpleHttpClient shc = SimpleHttpClient.getInstance(profile);
		RemoteCommandRequestHandler handler = new RemoteCommandRequestHandler();
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("command", intent.getStringExtra(KEY_KEYID)));
		params.add(new NameValuePair("rcu", "advanced"));
		String xml = handler.get(shc, params);

		if (xml != null) {
			ExtendedHashMap result = handler.parseSimpleResult(xml);
			if (Python.FALSE.equals(result.getString(SimpleResult.KEY_STATE))) {
				String errorText = result.getString(SimpleResult.KEY_STATE_TEXT, getString(R.string.connection_error));
				Log.w(TAG, result.getString(SimpleResult.KEY_STATE_TEXT));
				showToast(errorText);
			}
		} else if (shc.hasError()) {
			Log.w(TAG, shc.getErrorText(getBaseContext()));
			showToast(shc.getErrorText(getBaseContext()));
		}
	}

	private void doZapRequest(){

	}
}
