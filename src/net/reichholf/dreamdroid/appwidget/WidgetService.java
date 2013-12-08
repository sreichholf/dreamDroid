package net.reichholf.dreamdroid.appwidget;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
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

	public static final String ACTION_ZAP = "action_zap";
	public static final String ACTION_RCU = "action_rcu";

	private Handler mHandler;

	public WidgetService() {
		super(WidgetService.class.getCanonicalName());
	}

	@Override
	public void onCreate() {
		mHandler = new Handler();
		super.onCreate();
	}

	@Override
	public void onHandleIntent(Intent intent) {
		String action = intent.getAction();
		if(ACTION_RCU.equals(action))
			doRemoteRequest(intent);
		else if(ACTION_ZAP.equals(action))
			doZapRequest();
	}

	private void doRemoteRequest(Intent intent) {
		Profile profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(getApplicationContext(), intent.getIntExtra(KEY_WIDGETID, -1));

		SimpleHttpClient shc = SimpleHttpClient.getInstance(profile);
		RemoteCommandRequestHandler handler = new RemoteCommandRequestHandler();
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("command", intent.getStringExtra(KEY_KEYID)));
		params.add(new BasicNameValuePair("type", "advanced"));
		String xml = handler.get(shc, params);


		Toast toast = null;
		if (xml != null) {
			ExtendedHashMap result = handler.parseSimpleResult(xml);
			if (Python.FALSE.equals(result.getString(SimpleResult.KEY_STATE))) {
				String errorText = result.getString(SimpleResult.KEY_STATE_TEXT, getString(R.string.connection_error));
				Log.w(TAG, result.getString(SimpleResult.KEY_STATE_TEXT));
				showToast(errorText);
			}
		} else if (shc.hasError()) {
			Log.w(TAG, shc.getErrorText());
			showToast(shc.getErrorText());
		}
	}

	private void doZapRequest(){

	}

	/*
	 * show a toast and take care of calling it on the UI Thread
	 */
	private void showToast(final String text) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
			}
		});
	}
}
