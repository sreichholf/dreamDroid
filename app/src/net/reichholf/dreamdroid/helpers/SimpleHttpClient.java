/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

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
	private int mErrorTextId;

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
		String parms = NameValuePair.toString(parameters);
		if(!uri.contains("?"))
			uri += "?";
		return mPrefix + mProfile.getHost() + ":" + mProfile.getPortString() + uri + parms;
	}

	public String buildAuthedUrl(String uri, List<NameValuePair> parameters) {
		String parms = NameValuePair.toString(parameters);
		if(!uri.contains("?"))
			uri += "?";
		String loginString = "";
		if (mProfile.isLogin())
			loginString = String.format("%s:%s@", mProfile.getUser(), mProfile.getPass());

		return mPrefix + loginString + mProfile.getHost() + ":" + mProfile.getPortString() + uri + parms;
	}

	public String buildEncoderStreamUrl(String ref) {
		try {
			ref = URLEncoder.encode(ref, "utf-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
		}
		String streamLoginString = "";
		if (mProfile.isEncoderLogin())
			streamLoginString = mProfile.getEncoderUser() + ":" + mProfile.getEncoderPass() + "@";

		return String.format(
				"rtsp://%s%s:%s/%s?ref=%s&video_bitrate=%s&audio_bitrate=%s",
				streamLoginString,
				mProfile.getStreamHost(),
				mProfile.getEncoderPort(),
				mProfile.getEncoderPath(),
				ref,
				mProfile.getEncoderVideoBitrate(),
				mProfile.getEncoderAudioBitrate()
		);
	}

	public String buildStreamUrl(String ref) {
		if(mProfile.isEncoderStream())
			return buildEncoderStreamUrl(ref);
		else
			return buildServiceStreamUrl(ref);
	}

	/**
	 * @param ref
	 * @return
	 */
	public String buildServiceStreamUrl(String ref) {
		try {
			ref = URLEncoder.encode(ref, "utf-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
		}
		String streamLoginString = "";
		if (mProfile.isStreamLogin())
			streamLoginString = mProfile.getUser() + ":" + mProfile.getPass() + "@";

		return "http://" + streamLoginString + mProfile.getStreamHost() + ":" + mProfile.getStreamPortString() + "/" + ref;
	}

	public String buildFileStreamUrl(String ref, String fileName) {
		if(mProfile.isEncoderStream() && ref.startsWith("1:"))
			return buildEncoderStreamUrl(ref);

		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("file", fileName));
		String parms = NameValuePair.toString(params);
		String fileAuthString = "";
		if (mProfile.isFileLogin())
			fileAuthString = mProfile.getUser() + ":" + mProfile.getPass() + "@";

		return mFilePrefix + fileAuthString + mProfile.getStreamHost() + ":" + mProfile.getFilePortString() + URIStore.FILE + parms;
	}

	public boolean fetchPageContent(String uri) {
		return fetchPageContent(uri, new ArrayList<NameValuePair>());
	}

	private void setAuth(HttpURLConnection connection) {
		if (mProfile.isLogin()) {
			byte[] auth = (mProfile.getUser() + ":" + mProfile.getPass()).getBytes();
			String basic = Base64.encode(auth);
			connection.setRequestProperty("Authorization", "Basic " + basic);
		}
	}

	private boolean isSessionLess(String uri) {
		return URIStore.SCREENSHOT.equals(uri);
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
		mErrorTextId = -1;
		mError = false;
		mBytes = new byte[0];
		if (!uri.startsWith("/")) {
			uri = "/".concat(uri);
		}

		HttpURLConnection conn = null;
		try {
			if(mProfile.getSessionId() != null && !isSessionLess(uri))
				parameters.add(new NameValuePair("sessionid", mProfile.getSessionId()));
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
				Log.e(LOG_TAG, Integer.toString(conn.getResponseCode()));
				switch(conn.getResponseCode()){
					case HttpURLConnection.HTTP_UNAUTHORIZED:
						mErrorTextId = R.string.auth_error;
						break;
					default:
						mErrorTextId = -1;
				}
				mErrorText = conn.getResponseMessage();
				mError = true;
				return false;
			}

			BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int read = 0;
			int bufSize = 512;
			byte[] buffer = new byte[bufSize];
			while ((read = bis.read(buffer)) != -1) {
				bos.write(buffer, 0, read);
			}

			mBytes = bos.toByteArray();
			if (DreamDroid.dumpXml())
				dumpToFile(url);
			return true;

		} catch (MalformedURLException e) {
			mError = true;
			mErrorTextId = R.string.illegal_host;
		} catch (UnknownHostException e) {
			mError = true;
			mErrorText = null;
			mErrorTextId = R.string.host_not_found;
		} catch (ProtocolException e) {
			mError = true;
			mErrorText = e.getLocalizedMessage();
		} catch (ConnectException e) {
			mError = true;
			mErrorTextId = R.string.host_unreach;
		} catch (IOException e) {
			e.printStackTrace();
			mError = true;
			mErrorText = e.getLocalizedMessage();
		} catch (NullPointerException e) {
			e.printStackTrace();
			mError = true;
			mErrorText = e.getLocalizedMessage();
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


	public String getErrorText(Context context){
		if(mErrorTextId > 0)
			return context.getString(mErrorTextId);
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
