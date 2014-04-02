/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import android.os.Environment;
import android.util.Log;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.util.Base64;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.ByteArrayBuffer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * @author sreichholf
 */
public class SimpleHttpClient {
	public static String LOG_TAG = SimpleHttpClient.class.getSimpleName();

	private Profile mProfile;

	private String mPrefix;
	private String mFilePrefix;
	private byte[] mBytes;
	private String mErrorText;

	private boolean mError;
	private int mRememberedReturnCode;

	private int mConnectionTimeoutMillis = 3000;

	public SimpleHttpClient() {
		mProfile = null;
		init();
	}

	public SimpleHttpClient(Profile p) {
		mProfile = p;
		init();
	}

	/**
	 * @return
	 */
	public static SimpleHttpClient getInstance() {
		return new SimpleHttpClient();
	}

	public static SimpleHttpClient getInstance(Profile p) {
		return new SimpleHttpClient(p);
	}

	private void init() {
		//TODO Do not trust all hosts without asking the user
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		});

		applyConfig();
	}

	private void clearCredentials() {
		Authenticator.setDefault(null);
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public String buildUrl(String uri, List<NameValuePair> parameters) {
		String parms = URLEncodedUtils.format(parameters, HTTP.UTF_8).replace("+", "%20");
		if(!uri.contains("?"))
			uri += "?";
		return mPrefix + mProfile.getHost() + ":" + mProfile.getPortString() + uri + parms;
	}

	/**
	 * @param ref
	 * @param title
	 * @return
	 */
	public String buildServiceStreamUrl(String ref, String title) {
		try {
			ref = URLEncoder.encode(ref, HTTP.UTF_8).replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
		}
		String streamLoginString = "";
		if (mProfile.isStreamLogin())
			streamLoginString = mProfile.getUser() + ":" + mProfile.getPass() + "@";

		String url = "http://" + streamLoginString + mProfile.getStreamHost() + ":" + mProfile.getStreamPortString() + "/" + ref;
		return url;
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public String buildFileStreamUrl(String uri, List<NameValuePair> parameters) {
		String parms = URLEncodedUtils.format(parameters, HTTP.UTF_8).replace("+", "%20");
		String fileAuthString = "";
		if (mProfile.isFileLogin())
			fileAuthString = mProfile.getUser() + ":" + mProfile.getPass() + "@";

		String url = mFilePrefix + fileAuthString + mProfile.getStreamHost() + ":" + mProfile.getFilePortString() + uri + parms;
		return url;
	}

	public boolean fetchPageContent(String uri) {
		return fetchPageContent(uri, new ArrayList<NameValuePair>());
	}

	private void setAuth(HttpURLConnection connection) {
		if (mProfile.isLogin()) {
			byte[] auth = (mProfile.getUser() + ":" + mProfile.getPass()).getBytes();
			String basic = Base64.encodeToString(auth, Base64.NO_WRAP);
			connection.setRequestProperty("Authorization", "Basic " + basic);
		}
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
			if(mProfile.getSessionId() != null)
				parameters.add(new BasicNameValuePair("sessionid", mProfile.getSessionId()));
			URL url = new URL(buildUrl(uri, parameters));
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(mConnectionTimeoutMillis);
			if (DreamDroid.featurePostRequest())
				conn.setRequestMethod("POST");
			setAuth(conn);
			if (conn.getResponseCode() != 200) {
				if (conn.getResponseCode() == HttpURLConnection.HTTP_BAD_METHOD && mRememberedReturnCode != HttpURLConnection.HTTP_BAD_METHOD) {
					// Method not allowed, the target device either can't handle
					// POST or GET requests (old device or Anti-Hijack enabled)
					DreamDroid.setFeaturePostRequest(!DreamDroid.featurePostRequest());
					conn.disconnect();
					mRememberedReturnCode = HttpURLConnection.HTTP_BAD_METHOD;
					return fetchPageContent(uri, parameters);
				}
				if (conn.getResponseCode() == HttpURLConnection.HTTP_PRECON_FAILED && mRememberedReturnCode != HttpURLConnection.HTTP_PRECON_FAILED) {
					createSession();
					conn.disconnect();
					mRememberedReturnCode = HttpURLConnection.HTTP_PRECON_FAILED;
					return fetchPageContent(uri, parameters);
				}
				mRememberedReturnCode = 0;
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
			if (DreamDroid.dumpXml())
				dumpToFile(url);
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
				if (mErrorText == null)
					mErrorText = "Error text is null";
			Log.e(LOG_TAG, mErrorText);
		}

		return false;
	}

	private void createSession() {
		SimpleHttpClient shc = SimpleHttpClient.getInstance(mProfile);
		shc.fetchPageContent(URIStore.SESSION);
		if(!shc.hasError()) {
			String content = shc.getPageContentString();
			content = content.replaceAll("\\<.*?\\>", "").trim();
			mProfile.setSessionId(content);
		} else {
			mProfile.setSessionId(null);
		}
	}

	private void dumpToFile(URL url) {
		File externalStorage = Environment.getExternalStorageDirectory();
		if (!externalStorage.canWrite())
			return;

		String fn = null;

		String[] f = url.toString().split("/");
		fn = f[f.length - 1].split("\\?")[0];
		Log.w("--------------", fn);

		String base = String.format("%s/dreamDroid/xml", externalStorage);
		File file = new File(String.format("%s/%s", base, fn));
		BufferedOutputStream bos = null;
		try {
			(new File(base)).mkdirs();
			file.createNewFile();
			bos = new BufferedOutputStream(new FileOutputStream(file));
			bos.write(mBytes);
			bos.flush();
			bos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

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
		if (mProfile == null)
			mProfile = DreamDroid.getCurrentProfile();

		if (mProfile.isSsl()) {
			mPrefix = "https://";
		} else {
			mPrefix = "http://";
		}

		if (!mProfile.isLogin()) {
			clearCredentials();
		}

		if (mProfile.isFileSsl()) {
			mFilePrefix = "https://";
		} else {
			mFilePrefix = "http://";
		}
	}

	public void setConnectionTimeoutMillis(int millis) {
		mConnectionTimeoutMillis = millis;
	}
}
