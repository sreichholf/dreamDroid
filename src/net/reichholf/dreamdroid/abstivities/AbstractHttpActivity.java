/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.abstivities;

import java.util.HashMap;

import net.reichholf.dreamdroid.CustomExceptionHandler;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import android.app.Activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractHttpActivity extends Activity {
	protected SimpleHttpClient mShc;
	protected final String sData = "data";

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CustomExceptionHandler.register(this);
		
		if (savedInstanceState != null) {
			Object retained = getLastNonConfigurationInstance();
			if (retained instanceof HashMap) {
				mShc = (SimpleHttpClient) ((HashMap<String, Object>) retained).get("shc");
			}
		} else {
			setClient();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRetainNonConfigurationInstance()
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("shc", mShc);

		return map;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	/**
	 * 
	 */
	protected void setClient() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mShc = SimpleHttpClient.getInstance(sp);
	}

	/**
	 * @param toastText
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param toastText
	 */
	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}
}
