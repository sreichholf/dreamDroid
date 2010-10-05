/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;
import java.util.GregorianCalendar;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
//import android.webkit.WebSettings.ZoomDensity;
import android.webkit.WebView;

/**
 * Allows fetching and showing the actual TV-Screen content
 * 
 * @author sre
 * 
 */
public class ScreenShotActivity extends Activity {
	public static final int TYPE_OSD = 0;
	public static final int TYPE_VIDEO = 1;
	public static final int TYPE_ALL = 2;
	public static final int FORMAT_JPG = 0;
	public static final int FORMAT_PNG = 1;

	public static final int ITEM_RELOAD = 0;
	
	public static final String KEY_TYPE = "type";
	public static final String KEY_FORMAT = "format";
	public static final String KEY_SIZE = "size";
	public static final String KEY_FILENAME = "filename";

	private WebView mWebView;
	private int mType;
	private int mFormat;
	private int mSize;
	private String mFilename;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle( getText(R.string.app_name) + " - " + getText(R.string.screenshot) );

		setContentView(R.layout.web_view);
		mWebView = (WebView) findViewById(R.id.web);

		mWebView.setBackgroundColor(Color.BLACK);
		mWebView.getSettings().setBuiltInZoomControls(true);
//		mWebView.getSettings().setDefaultZoom(ZoomDensity.FAR);
		
		Bundle extras = getIntent().getExtras();

		if (extras == null) {
			extras = new Bundle();
		}

		mType = extras.getInt(KEY_TYPE, TYPE_ALL);
		mFormat = extras.getInt(KEY_FORMAT, FORMAT_PNG);
		mSize = extras.getInt(KEY_SIZE, 360);
		mFilename = extras.getString(KEY_FILENAME);

		reload();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, ITEM_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		reload();
		return true;
	}
	
	private void reload() {
		ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();

		switch (mType) {
		case (TYPE_OSD):
			parameters.add(new BasicNameValuePair("o", ""));
			parameters.add(new BasicNameValuePair("n", ""));
			break;
		case (TYPE_VIDEO):
			parameters.add(new BasicNameValuePair("v", ""));
			break;
		case (TYPE_ALL):
			break;
		}

		switch (mFormat) {
		case (FORMAT_JPG):
			parameters.add(new BasicNameValuePair("format", "jpg"));
			break;
		case (FORMAT_PNG):
			parameters.add(new BasicNameValuePair("format", "png"));
			break;
		}

		parameters.add(new BasicNameValuePair("r", new Integer(mSize)
				.toString()));

		if (mFilename == null) {
			long ts = ( new GregorianCalendar().getTimeInMillis() ) / 1000;
			mFilename = "/tmp/dreamDroid-" + ts;
		}
		parameters.add(new BasicNameValuePair("filename", mFilename));

		String url = SimpleHttpClient.getInstance().buildUrl(
				URIStore.SCREENSHOT, parameters);
		mWebView.loadUrl(url);
	}

}
