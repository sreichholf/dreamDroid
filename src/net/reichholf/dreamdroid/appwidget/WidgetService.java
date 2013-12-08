package net.reichholf.dreamdroid.appwidget;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;

/**
 * Created by Stephan on 08.12.13.
 */
public class WidgetService extends IntentService {
	public static String TAG = WidgetService.class.getSimpleName();
	public static final String KEY_KEYID = "key_id";
	public static final String KEY_WIDGETID = "widget_id";

	public WidgetService() {
		super(WidgetService.class.getCanonicalName());
	}

	@Override
	public void onHandleIntent(Intent intent) {
		Profile profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(getApplicationContext(), intent.getIntExtra(KEY_WIDGETID, -1));

		SimpleHttpClient shc = SimpleHttpClient.getInstance(profile);
		RemoteCommandRequestHandler handler = new RemoteCommandRequestHandler();
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("command", intent.getStringExtra(KEY_KEYID)));
		String xml = handler.get(shc, params);

		if (xml != null) {
			ExtendedHashMap result = handler.parseSimpleResult(xml);
			if(Python.FALSE.equals(result.getString(SimpleResult.KEY_STATE)))
				Log.w(TAG, result.getString(SimpleResult.KEY_STATE_TEXT));
		}
	}
}
