package net.reichholf.dreamdroid.tv.activities;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.UrlConnectionDownloader;

import net.reichholf.dreamdroid.R;

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;

/**
 * Created by Stephan on 16.10.2016.
 */

public class MainActivity extends FragmentActivity {
	private MemorizingTrustManager mTrustManager;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tv_main);
		try {
			// set location of the keystore
			MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
			// register MemorizingTrustManager for HTTPS
			SSLContext sc = SSLContext.getInstance("TLS");
			mTrustManager = new MemorizingTrustManager(this);
			sc.init(null, new X509TrustManager[]{mTrustManager},
					new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(
					mTrustManager.wrapHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()));
			Picasso.Builder builder = new Picasso.Builder(this);
			builder.downloader(new UrlConnectionDownloader(this){
				@Override
				protected HttpURLConnection openConnection(Uri path) throws IOException {
					HttpURLConnection connection = super.openConnection(path);
					String userinfo = path.getUserInfo();
					if(!userinfo.isEmpty()) {
						connection.setRequestProperty("Authorization", "Basic " +
								Base64.encodeToString(userinfo.getBytes(), Base64.NO_WRAP));
					}
					return connection;
				}
			});
			Picasso.setSingletonInstance(builder.build());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
