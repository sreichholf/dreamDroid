/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import java.io.IOException;
import java.util.List;

import net.reichholf.dreamdroid.DreamDroid;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import android.util.Log;

/**
 * @author sreichholf
 * 
 */
public class SimpleHttpClient {
	private UsernamePasswordCredentials mCreds = null;
	private DefaultHttpClient mDhc;
	private HttpContext mContext;

	private String mPrefix;
	private String mHostname;
	private String mStreamHostname;
	private String mPort;
	private String mUser;
	private String mPass;
	private byte[] mBytes;
	private String mErrorText;

	private boolean mLogin;
	private boolean mSsl;
	private boolean mError;

	/**
	 * @param sp
	 *            SharedPreferences of the Base-Context
	 */
	private SimpleHttpClient() {
		BasicHttpParams params = new BasicHttpParams();
		params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000);

		mDhc = new DefaultHttpClient(getEasySSLClientConnectionManager(), params);

		mContext = new BasicHttpContext();
		applyConfig();
	}

	/**
	 * @return ThreadSafeClientConnManager Instance
	 */
	public ThreadSafeClientConnManager getEasySSLClientConnectionManager() {
		BasicHttpParams params = new BasicHttpParams();

		SchemeRegistry schemeRegistry = new SchemeRegistry();

		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));

		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

		return cm;
	}

	/**
	 * @param user
	 *            Username for http-auth
	 * @param pass
	 *            Password for http-auth
	 */
	public void setCredentials(String user, String pass) {
		mCreds = new UsernamePasswordCredentials(user, pass);
		mDhc.getCredentialsProvider().setCredentials(AuthScope.ANY, mCreds);
	}

	/**
	 * 
	 */
	public void unsetCrendentials() {
		mDhc.getCredentialsProvider().setCredentials(AuthScope.ANY, null);
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public String buildUrl(String uri, List<NameValuePair> parameters) {
		String parms = URLEncodedUtils.format(parameters, HTTP.ISO_8859_1);
		return mPrefix + mHostname + ":" + mPort + uri + parms;
	}
	
	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public String buildStreamUrl(String uri, List<NameValuePair> parameters) {
		String parms = URLEncodedUtils.format(parameters, HTTP.ISO_8859_1);
		return "http://" + mStreamHostname + ":" + 80 + uri + parms;
	}

	/**
	 * @param uri
	 * @param parameters
	 * @return
	 */
	public boolean fetchPageContent(String uri, List<NameValuePair> parameters) {
		// Set login, ssl, port, host etc;
		applyConfig();

		mErrorText = null;
		mError = false;
		mBytes = new byte[0];
		if (!uri.startsWith("/")) {
			uri = "/".concat(uri);
		}

		String url = buildUrl(uri, parameters);

		try {
			HttpGet get = new HttpGet(url);
			HttpResponse resp = mDhc.execute(get, mContext);
			StatusLine s = resp.getStatusLine();

			if (s.getStatusCode() == HttpStatus.SC_OK) {
				HttpEntity entity = resp.getEntity();
				if (entity != null) {
					mBytes = EntityUtils.toByteArray(entity);
					return true;

				} else {
					mErrorText = "HttpEntity is null";
					mError = true;
				}

			} else {
				mErrorText = s.getStatusCode() + " - " + s.getReasonPhrase();
				mError = true;
				return false;
			}

		} catch (IllegalArgumentException e) {
			Log.e(getClass().getSimpleName(), e.toString());
			mErrorText = e.getClass().getSimpleName().replace("Exception", "");
			if (e.getMessage() != null) {
				mErrorText += ": " + e.getMessage();
			}
			mError = true;
			return false;

		} catch (ClientProtocolException e) {
			Log.e(getClass().getSimpleName(), e.toString());

			mErrorText = e.getClass().getSimpleName().replace("Exception", "");
			if (e.getMessage() != null) {
				mErrorText += ": " + e.getMessage();
			}

			mError = true;
			return false;

		} catch (IOException e) {
			Log.e(getClass().getSimpleName(), e.toString());

			mErrorText = e.getClass().getSimpleName().replace("Exception", "");
			if (e.getMessage() != null) {
				mErrorText += ": " + e.getMessage();
			}

			mError = true;
			return false;
		}

		return false;
	}

	/**
	 * @return
	 */
	public String getPageContentString() {
		return new String(mBytes);
	}
	
	public byte[] getBytes(){
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
		mHostname = DreamDroid.PROFILE.getHost().trim();
		mStreamHostname = DreamDroid.PROFILE.getStreamHost().trim();
		mPort = new Integer(DreamDroid.PROFILE.getPort()).toString();
		mLogin = DreamDroid.PROFILE.isLogin();
		mSsl = DreamDroid.PROFILE.isSsl();

		if (mSsl) {
			mPrefix = "https://";
		} else {
			mPrefix = "http://";
		}

		if (mLogin) {
			mUser = DreamDroid.PROFILE.getUser();
			mPass = DreamDroid.PROFILE.getPass();
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
