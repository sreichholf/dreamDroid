package net.reichholf.dreamdroid.tv.activities;

import android.os.Bundle;

import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import net.reichholf.dreamdroid.R;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import de.duenndns.ssl.JULHandler;
import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.internal.tls.OkHostnameVerifier;

/**
 * Created by Stephan on 16.10.2016.
 */

public class MainActivity extends FragmentActivity {
	private MemorizingTrustManager mTrustManager;

	private int responseCount(Response response) {
		int result = 1;
		while ((response = response.priorResponse()) != null) {
			result++;
		}
		return result;
	}

	private static X509TrustManager systemDefaultTrustManager() {
		try {
			TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init((KeyStore) null);
			TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
			if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
				throw new IllegalStateException("Unexpected default trust managers:"
						+ Arrays.toString(trustManagers));
			}
			return (X509TrustManager) trustManagers[0];
		} catch (GeneralSecurityException e) {
			throw new AssertionError(); // The system has no TLS. Just give up.
		}
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tv_main);
		try {
			// set location of the keystore
			JULHandler.initialize();
			JULHandler.setDebugLogSettings(() -> false);
			// register MemorizingTrustManager for HTTPS

			mTrustManager = new MemorizingTrustManager(this);

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[]{mTrustManager},
					new java.security.SecureRandom());

			//HttpsURLConnection
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(
					mTrustManager.wrapHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()));
			HttpsURLConnection.setFollowRedirects(false);
			//Picasso w/ OkHttpClient

			OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
			clientBuilder
					.authenticator((route, response) -> {
						if (responseCount(response) >= 3) {
							return null;
						}
						String username = response.request().url().username();
						String password = response.request().url().password();
						String cred = Credentials.basic(username, password);
						return response.request().newBuilder().header("Authorization", cred).build();
					})
					.sslSocketFactory(sc.getSocketFactory(), systemDefaultTrustManager())
					.hostnameVerifier(mTrustManager.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE));
			Picasso.Builder builder = new Picasso.Builder(getApplicationContext());
			builder.downloader(new OkHttp3Downloader(clientBuilder.build()));
			try {
				Picasso.setSingletonInstance(builder.build());
			} catch (IllegalStateException ignored) {}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
