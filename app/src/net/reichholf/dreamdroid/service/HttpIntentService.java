package net.reichholf.dreamdroid.service;

import android.os.Handler;
import android.widget.Toast;

import androidx.core.app.JobIntentService;

import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/**
 * Created by Stephan on 04.06.2014.
 */
public abstract class HttpIntentService extends JobIntentService {
	protected Handler mHandler;
	protected DreamDroidTrustManager mTrustManager;

	@Override
	public void onCreate() {
		mHandler = new Handler();
		super.onCreate();
	}

	protected void setupSSL() {
		if(!HttpsURLConnection.getDefaultSSLSocketFactory().getClass().equals(DreamDroidTrustManager.class)){
			try {
				// register MemorizingTrustManager for HTTPS
				SSLContext sc = SSLContext.getInstance("TLS");
				mTrustManager = new DreamDroidTrustManager(getApplicationContext());
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
		mHandler.post(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show());
	}
}
