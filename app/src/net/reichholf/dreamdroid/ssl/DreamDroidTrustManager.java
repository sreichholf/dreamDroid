package net.reichholf.dreamdroid.ssl;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.DreamDroid;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class DreamDroidTrustManager implements HostnameVerifier, X509TrustManager {
	private static String LOG_TAG = DreamDroidTrustManager.class.getSimpleName();
	X509TrustManager mDefaultTrustManager;
	HostnameVerifier mDefaultHostnameVerifier;

	public DreamDroidTrustManager(Context ctx) {
		mDefaultTrustManager = getDefaultTrustManager();
		mDefaultHostnameVerifier = null;
	}

	X509TrustManager getDefaultTrustManager() {
		try {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
			tmf.init((KeyStore)null);
			for (TrustManager t : tmf.getTrustManagers()) {
				if (t instanceof X509TrustManager) {
					return (X509TrustManager)t;
				}
			}
		} catch (Exception e) {
			Log.w(LOG_TAG, "getDefaultTrustManager(): " + e);
		}
		return null;
	}

	public HostnameVerifier wrapHostnameVerifier(@NonNull final HostnameVerifier verifier) {
		mDefaultHostnameVerifier = verifier;
		return this;
	}

	public boolean trustAllCertificates() {
		return DreamDroid.getCurrentProfile().isAllCertsTrusted();
	}

	@Override
	public boolean verify(String hostname, SSLSession session) {
		if (trustAllCertificates())
			return true;
		return mDefaultHostnameVerifier.verify(hostname, session);
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (!trustAllCertificates())
			mDefaultTrustManager.checkClientTrusted(chain, authType);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (!trustAllCertificates())
			mDefaultTrustManager.checkServerTrusted(chain, authType);
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}
}
