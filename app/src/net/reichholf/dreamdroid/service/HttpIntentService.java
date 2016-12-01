package net.reichholf.dreamdroid.service;

import android.app.IntentService;
import android.os.Handler;
import android.widget.Toast;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;

/**
 * Created by Stephan on 04.06.2014.
 */
public abstract class HttpIntentService extends IntentService{
	protected Handler mHandler;
	protected MemorizingTrustManager mTrustManager;

	public HttpIntentService(String name) {
		super(name);
	}

	@Override
	public void onCreate() {
		mHandler = new Handler();
		super.onCreate();
	}

	protected void setupSSL() {
		if(!HttpsURLConnection.getDefaultSSLSocketFactory().getClass().equals(MemorizingTrustManager.class)){
			try {
				// set location of the keystore
				MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
				// register MemorizingTrustManager for HTTPS
				SSLContext sc = SSLContext.getInstance("TLS");
				mTrustManager = new MemorizingTrustManager(getApplicationContext());
				sc.init(null, new X509TrustManager[] { mTrustManager },
						new java.security.SecureRandom());
				HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
				HttpsURLConnection.setDefaultHostnameVerifier(
						mTrustManager.wrapHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/*
 * show a toast and take care of calling it on the UI Thread
 */
	protected void showToast(final String text) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
			}
		});
	}
}
