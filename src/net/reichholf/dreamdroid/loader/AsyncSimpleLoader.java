/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.loader;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractSimpleRequestHandler;

import org.apache.http.NameValuePair;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;

/**
 * @author sre
 * 
 */
public class AsyncSimpleLoader extends AsyncTaskLoader< LoaderResult<ExtendedHashMap> > {

	private AbstractSimpleRequestHandler mHandler;
	private SimpleHttpClient mShc;
	protected ArrayList<NameValuePair> mParams;

	public AsyncSimpleLoader(Context context, AbstractSimpleRequestHandler handler, Bundle args) {
		super(context);
		init(context, handler, args);
	}

	@SuppressWarnings("unchecked")
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
	public LoaderResult<ExtendedHashMap> loadInBackground() {
		ExtendedHashMap content = new ExtendedHashMap();
		String xml = null;
		if (mParams == null)
			xml = mHandler.get(mShc);
		else
			xml = mHandler.get(mShc, mParams);
		
		LoaderResult<ExtendedHashMap> result = new LoaderResult<ExtendedHashMap>();
		if (xml != null) {
			if (mHandler.parse(xml, content))
				result.set(content);
			else
				result.set(getContext().getString(R.string.error_parsing));
		} else {
			if(mShc.hasError())
				result.set(mShc.getErrorText());
			else
				result.set(getContext().getString(R.string.error));
		}
		return result;
	}

}
