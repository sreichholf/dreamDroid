/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.loader;

import java.util.ArrayList;

import org.apache.http.NameValuePair;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractSimpleRequestHandler;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.content.Context;

/**
 * @author sre
 * 
 */
public class AsyncSimpleLoader extends AsyncTaskLoader<ExtendedHashMap> {

	private AbstractSimpleRequestHandler mHandler;
	private SimpleHttpClient mShc;
	protected ArrayList<NameValuePair> mParams;

	public AsyncSimpleLoader(Context context, AbstractSimpleRequestHandler handler, Bundle args) {
		super(context);
		init(context, handler, args);
	}

	private void init(Context context, AbstractSimpleRequestHandler handler, Bundle args) {
		mHandler = handler;
		mShc = new SimpleHttpClient();
		if (args != null && args.containsKey("params"))
			mParams = (ArrayList<NameValuePair>) args.getSerializable("params");
		else
			mParams = null;
	}

	@Override
	protected void onStartLoading() {
		forceLoad();
	}

	@Override
	protected void onStopLoading() {
		// Attempt to cancel the current load task if possible.
		cancelLoad();
	}

	@Override
	public ExtendedHashMap loadInBackground() {
		ExtendedHashMap content = new ExtendedHashMap();
		String xml = null;
		if (mParams == null)
			xml = mHandler.get(mShc);
		else
			xml = mHandler.get(mShc, mParams);

		if (xml != null) {
			mHandler.parse(xml, content);
		}
		return content;
	}

}
