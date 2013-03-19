/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.ByteArrayBuffer;

import android.util.Log;

/**
 * @author sreichholf
 * 
 */
public class SimpleHttpClient {
	public static String LOG_TAG = "SimpleHttpClient";

	private String mPrefix;
	private String mHostname;
	private String mStreamHostname;
	private String mPort;
	private String mFilePort;
	private String mUser;
	private String mPass;
	private byte[] mBytes;
	private String mErrorText;

	private boolean mLogin;
	private boolean mSsl;
	private boolean mError;
	private boolean mIsLoopProtected;

	/**
	 * @param sp
	 *            SharedPreferences of the Base-Context
	 */
	public SimpleHttpClient() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new EasyX509TrustManager(null) }, null);
			HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
		} catch (KeyManagementException e) {
		} catch (NoSuchAlgorithmException e) {
		} catch (KeyStoreException e) {
		}

		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		});

		applyConfig();
	}

	/**
	 * @param user
	 *            Username for http-auth
	 * @param pass
	 *            Password for http-auth
	 */
	public void setCredentials(final String user, final String pass) {
		Authenticator.setDefault(new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(user, pass.toCharArray());
			}
		});
	}

	/**
	 * 
	 */
	public void unsetCrendentials() {
		// mDhc.getCredentialsProvider().setCredentials(AuthScope.ANY, null);
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public String buildUrl(String uri, List<NameValuePair> parameters) {
		String parms = URLEncodedUtils.format(parameters, HTTP.UTF_8).replace("+", "%20");
		return mPrefix + mHostname + ":" + mPort + uri + parms;
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public String buildFileStreamUrl(String uri, List<NameValuePair> parameters) {
		String parms = URLEncodedUtils.format(parameters, HTTP.UTF_8).replace("+", "%20");
		return "http://" + mStreamHostname + ":" + mFilePort + uri + parms;
	}

	public boolean fetchPageContent(String uri) {
		return fetchPageContent(uri, new ArrayList<NameValuePair>());
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public boolean fetchPageContent(String uri, List<NameValuePair> parameters) {
		// Set login, ssl, port, host etc;
		applyConfig();

		mErrorText = "";
		mError = false;
		mBytes = new byte[0];
		if (!uri.startsWith("/")) {
			uri = "/".concat(uri);
		}

		HttpURLConnection conn = null;
		try {
			URL url = new URL(buildUrl(uri, parameters));
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(10000);
			if (DreamDroid.featurePostRequest())
				conn.setRequestMethod("POST");

			if (conn.getResponseCode() != 200) {
				if (conn.getResponseCode() == 405 && !mIsLoopProtected) {
					// Method not allowed, the target device either can't handle
					// POST or GET requests (old device or Anti-Hijack enabled)
					DreamDroid.setFeaturePostRequest(!DreamDroid.featurePostRequest());
					conn.disconnect();
					mIsLoopProtected = true;
					return fetchPageContent(uri, parameters);
				}
				mIsLoopProtected = false;
				mErrorText = conn.getResponseMessage();
				mError = true;
				return false;
			}

			BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
			ByteArrayBuffer baf = new ByteArrayBuffer(50);
			int read = 0;
			int bufSize = 512;
			byte[] buffer = new byte[bufSize];
			while ((read = bis.read(buffer)) != -1) {
				baf.append(buffer, 0, read);
			}

			mBytes = baf.toByteArray();
			return true;

		} catch (MalformedURLException e) {
			mError = true;
			mErrorText = e.toString();
		} catch (IOException e) {
			mError = true;
			mErrorText = e.toString();
		} catch (Exception e) {
			mError = true;
			mErrorText = e.toString();
		} finally {
			if (conn != null)
				conn.disconnect();
			if (mError)
				if(mErrorText == null)
					mErrorText = "Error text is null";
				Log.e(LOG_TAG, mErrorText);
		}

		return false;
	}

	/**
	 * @return
	 */
	public String getPageContentString() {
		return new String(mBytes);
	}

	public byte[] getBytes() {
		return mBytes;
	}

	/**
	 * @return
	 */
	public String getErrorText() {
		return mErrorText;
	}

	/**
	 * @return
	 */
	public boolean hasError() {
		return mError;
	}

	/**
	 * 
	 */
	public void applyConfig() {
		Profile p = DreamDroid.getCurrentProfile();
		mHostname = p.getHost().trim();
		mStreamHostname = p.getStreamHost().trim();
		mPort = p.getPortString();
		mFilePort = p.getFilePortString();
		mLogin = p.isLogin();
		mSsl = p.isSsl();

		if (mSsl) {
			mPrefix = "https://";
		} else {
			mPrefix = "http://";
		}

		if (mLogin) {
			mUser = p.getUser();
			mPass = p.getPass();
			setCredentials(mUser, mPass);
		}
	}

	/**
	 * @param sp
	 * @return
	 */
	public static SimpleHttpClient getInstance() {
		return new SimpleHttpClient();
	}
}
