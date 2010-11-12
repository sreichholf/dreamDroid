/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;
import java.util.GregorianCalendar;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpActivity;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ImageView;

/**
 * Allows fetching and showing the actual TV-Screen content
 * 
 * @author sre
 * 
 */
public class ScreenShotActivity extends AbstractHttpActivity {
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

	private ImageView mImageView;
	private int mType;
	private int mFormat;
	private int mSize;
	private String mFilename;
	@SuppressWarnings("unused")
	private byte[] mRawImage;
	private GetScreenshotTask mGetScreenshotTask;
	
	private class GetScreenshotTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private byte[] mBytes;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress();
			mShc.fetchPageContent(URIStore.SCREENSHOT, params[0]);
			mBytes = mShc.getBytes();

			if(mBytes.length > 0){
				return true;
			}			

			return false;
		}
		
		@Override
		protected void onProgressUpdate(Void... progress) {
			updateProgress();
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			if (result) {
				onScreenshotAvailable(mBytes);
			} else {
				String error = getString(R.string.get_content_error);
				if(mShc.hasError()){
					error = error.concat("\n").concat(mShc.getErrorText());
				}
				showToast(error);
			}
		}
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		super.onCreate(savedInstanceState);		
		setTitle(getText(R.string.app_name) + " - " + getText(R.string.screenshot));
		
		mImageView = new ImageView(this);
		setContentView(mImageView);
		mImageView.setBackgroundColor(Color.BLACK);

		Bundle extras = getIntent().getExtras();

		if (extras == null) {
			extras = new Bundle();
		}

		mType = extras.getInt(KEY_TYPE, TYPE_ALL);
		mFormat = extras.getInt(KEY_FORMAT, FORMAT_PNG);
		mSize = extras.getInt(KEY_SIZE, 480);
		mFilename = extras.getString(KEY_FILENAME);

		reload();
	}

	/*
	 * (non-Javadoc)
	 * 
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

	private void updateProgress(){
		setProgressBarIndeterminateVisibility(true);
	}
	
	private void onScreenshotAvailable(byte[] bytes){
		mRawImage = bytes;
		mImageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
		setProgressBarIndeterminateVisibility(false);
	}
	
	@SuppressWarnings("unchecked")
	private void reload() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		switch (mType) {
		case (TYPE_OSD):
			params.add(new BasicNameValuePair("o", ""));
			params.add(new BasicNameValuePair("n", ""));
			break;
		case (TYPE_VIDEO):
			params.add(new BasicNameValuePair("v", ""));
			break;
		case (TYPE_ALL):
			break;
		}

		switch (mFormat) {
		case (FORMAT_JPG):
			params.add(new BasicNameValuePair("format", "jpg"));
			break;
		case (FORMAT_PNG):
			params.add(new BasicNameValuePair("format", "png"));
			break;
		}

		params.add(new BasicNameValuePair("r", new Integer(mSize).toString()));

		if (mFilename == null) {
			long ts = (new GregorianCalendar().getTimeInMillis()) / 1000;
			mFilename = "/tmp/dreamDroid-" + ts;
		}
		params.add(new BasicNameValuePair("filename", mFilename));
		
		if(mGetScreenshotTask != null){
			if(mGetScreenshotTask.getStatus().equals(AsyncTask.Status.RUNNING)){
				mGetScreenshotTask.cancel(true);
			}
		}
		mGetScreenshotTask = new GetScreenshotTask();
		mGetScreenshotTask.execute(params);
	}
}
