package net.reichholf.dreamdroid.activities.abs;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import net.reichholf.dreamdroid.ssl.MemorizingTrustManager;

/**
 * Created by Stephan on 06.11.13.
 */
public class BaseActivity extends ActionBarActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			// set location of the keystore
			MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
			// register MemorizingTrustManager for HTTPS
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, MemorizingTrustManager.getInstanceList(this),
					new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onCreate(savedInstanceState);
	}

	public void onPause(){
		super.onPause();
	}

	public void onResume(){
		super.onResume();
	}
}
