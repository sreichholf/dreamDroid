/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.interfaces;

import android.os.Bundle;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

import org.apache.http.NameValuePair;

import java.util.ArrayList;

/**
 * @author sre
 * 
 */
public interface HttpBaseFragment {
	public String getBaseTitle();

	public void setBaseTitle(String baseTitle);

	public String getCurrentTitle();

	public void setCurrentTitle(String currentTitle);

	public ArrayList<NameValuePair> getHttpParams(int loader);

	public Bundle getLoaderBundle(int loader);

	public String getLoadFinishedTitle();

	public SimpleHttpClient getHttpClient();

	public void onProfileChanged();
	
	public abstract void onSimpleResult(boolean success, ExtendedHashMap result);
}
