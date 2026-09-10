package net.reichholf.dreamdroid.appwidget;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler;
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/**
 * Home-screen Virtual Remote click handler. Runs RCU HTTP off the main thread
 * (replaces the old {@code JobIntentService} WidgetService path).
 * Uses a small thread pool so rapid taps do not queue behind one request while
 * holding {@link android.content.BroadcastReceiver.PendingResult} from {@code goAsync()}.
 */
public final class WidgetRemoteRequest {
	private static final String TAG = WidgetRemoteRequest.class.getSimpleName();

	/** Stable action string (kept for existing PendingIntents). */
	public static final String ACTION_RCU =
			"net.reichholf.dreamdroid.appwidget.WidgetService.ACTION_RCU";

	public static final String KEY_KEYID = "key_id";
	public static final String KEY_WIDGETID = "widget_id";

	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
	private static final Handler MAIN = new Handler(Looper.getMainLooper());

	private WidgetRemoteRequest() {
	}

	public static void enqueue(@NonNull Context context, @NonNull Intent intent,
							   @Nullable final Runnable onComplete) {
		Context app = context.getApplicationContext();
		EXECUTOR.execute(() -> {
			try {
				doRemoteRequest(app, intent);
			} finally {
				if (onComplete != null) {
					onComplete.run();
				}
			}
		});
	}

	public static void enqueue(@NonNull Context context, @NonNull Intent intent) {
		enqueue(context, intent, null);
	}

	private static void doRemoteRequest(@NonNull Context context, @NonNull Intent intent) {
		setupSSL(context);

		Profile profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(
				context, intent.getIntExtra(KEY_WIDGETID, -1));
		if (profile == null) {
			return;
		}

		SimpleHttpClient shc = SimpleHttpClient.getInstance(profile);
		RemoteCommandRequestHandler handler = new RemoteCommandRequestHandler();
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("command", intent.getStringExtra(KEY_KEYID)));
		params.add(new NameValuePair("rcu", "advanced"));
		String xml = handler.get(shc, params);

		if (xml != null) {
			ExtendedHashMap result = handler.parseSimpleResult(xml);
			if (Python.FALSE.equals(result.getString(SimpleResult.KEY_STATE))) {
				String errorText = result.getString(SimpleResult.KEY_STATE_TEXT,
						context.getString(R.string.connection_error));
				Log.w(TAG, result.getString(SimpleResult.KEY_STATE_TEXT));
				showToast(context, errorText);
			}
		} else if (shc.hasError()) {
			Log.w(TAG, shc.getErrorText(context));
			showToast(context, shc.getErrorText(context));
		}
	}

	private static void setupSSL(@NonNull Context context) {
		if (!HttpsURLConnection.getDefaultSSLSocketFactory().getClass()
				.equals(DreamDroidTrustManager.class)) {
			try {
				SSLContext sc = SSLContext.getInstance("TLS");
				DreamDroidTrustManager trustManager = new DreamDroidTrustManager(context);
				sc.init(null, new X509TrustManager[]{trustManager},
						new java.security.SecureRandom());
				HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
				HttpsURLConnection.setDefaultHostnameVerifier(
						trustManager.wrapHostnameVerifier(
								HttpsURLConnection.getDefaultHostnameVerifier()));
			} catch (Exception e) {
				Log.w(TAG, "SSL setup failed", e);
			}
		}
	}

	private static void showToast(@NonNull Context context, final String text) {
		MAIN.post(() -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show());
	}
}
