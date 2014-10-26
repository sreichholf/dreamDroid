/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;


import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.DreamDroidFragment;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidHttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.loader.AsyncByteLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * Allows fetching and showing the actual TV-Screen content
 * 
 * @author sre
 * 
 */
public class ScreenShotFragment extends DreamDroidFragment implements
		LoaderCallbacks<LoaderResult<byte[]>>, SwipeRefreshLayout.OnRefreshListener {
	public static final int TYPE_OSD = 0;
	public static final int TYPE_VIDEO = 1;
	public static final int TYPE_ALL = 2;
	public static final int FORMAT_JPG = 0;
	public static final int FORMAT_PNG = 1;

	public static final String KEY_TYPE = "type";
	public static final String KEY_FORMAT = "format";
	public static final String KEY_SIZE = "size";
	public static final String KEY_FILENAME = "filename";

	private static final String BUNDLE_KEY_RETAIN = "retain";

    private boolean mActionsEnabled;
	private ImageView mImageView;
	private int mType;
	private int mFormat;
	private int mSize;
	private String mFilename;
	private byte[] mRawImage;
	private MediaScannerConnection mScannerConn;
	private PhotoViewAttacher mAttacher;
	private DreamDroidHttpFragmentHelper mHttpHelper;

	@Override
	public void onRefresh() {
		reload();
	}

	private class DummyMediaScannerConnectionClient implements MediaScannerConnectionClient {
		@Override
		public void onMediaScannerConnected() {
			return;
		}

		@Override
		public void onScanCompleted(String arg0, Uri arg1) {
			return;
		}

	}

	public ScreenShotFragment(){
		super();
		shouldRetain(true);
		mHttpHelper = new DreamDroidHttpFragmentHelper();
        mActionsEnabled = true;
	}

	public ScreenShotFragment(boolean retainInstance, boolean actionsEnabled){
		super();
		shouldRetain(retainInstance);
        mActionsEnabled = actionsEnabled;
	}

	private void shouldRetain(boolean retainInstance){
		Bundle args = new Bundle();
		args.putBoolean(BUNDLE_KEY_RETAIN, retainInstance);
		setArguments(args);

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mShouldRetainInstance = getArguments().getBoolean(BUNDLE_KEY_RETAIN);
		super.onCreate(savedInstanceState);
		if (mHttpHelper == null)
			mHttpHelper = new DreamDroidHttpFragmentHelper(this);
		else
			mHttpHelper.bindToFragment(this);

		setHasOptionsMenu(true);
		initTitles(getString(R.string.screenshot));
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getActionBarActivity().setTitle(getText(R.string.screenshot));
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.screenshot, null);

		mImageView = (ImageView) view.findViewById(R.id.screenshoot);
		mImageView.setBackgroundColor(Color.BLACK);

		Bundle extras = getArguments();

		if (extras == null) {
			extras = new Bundle();
		}

		mType = extras.getInt(KEY_TYPE, TYPE_ALL);
		mFormat = extras.getInt(KEY_FORMAT, FORMAT_PNG);
		mSize = extras.getInt(KEY_SIZE, 720);
		mFilename = extras.getString(KEY_FILENAME);

		if (savedInstanceState != null) {
			mRawImage = savedInstanceState.getByteArray("rawImage");
		} else if (mRawImage == null) {
			mRawImage = new byte[0];
		}
		mAttacher = new PhotoViewAttacher(mImageView);
		return view;
	}

	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		mHttpHelper.onViewCreated(view, savedInstanceState);
		SwipeRefreshLayout SwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.ptr_layout);
		SwipeRefreshLayout.setEnabled(false);
	}

	@Override
	public void onResume() {
		super.onResume();
		mScannerConn = new MediaScannerConnection(getActionBarActivity(), new DummyMediaScannerConnectionClient());
		mScannerConn.connect();

		if (mRawImage.length == 0) {
			reload();
		} else {
			onScreenshotAvailable(mRawImage);
		}
	}

	@Override
	public void onPause() {
		mScannerConn.disconnect();
		super.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mAttacher.cleanup();
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
        if(menu.findItem(R.id.menu_reload) == null && mActionsEnabled) {
            inflater.inflate(R.menu.reload, menu);
            inflater.inflate(R.menu.save, menu);
        }
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		mScannerConn.disconnect();
		outState.putByteArray("rawImage", mRawImage);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case Statics.ITEM_RELOAD:
				reload();
				break;
			case Statics.ITEM_SAVE:
				saveToFile();
		}

		return true;
	}

		/**
		 * @param bytes
		 */
	private void onScreenshotAvailable(byte[] bytes) {
		if (this.isDetached())
			return;
		mRawImage = bytes;
		mImageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
		mAttacher.update();
	}

	protected void reload() {
		mHttpHelper.onLoadStarted();
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

		params.add(new BasicNameValuePair("r", String.valueOf(mSize)));

		if (mFilename == null) {
			long ts = (new GregorianCalendar().getTimeInMillis()) / 1000;
			mFilename = "/tmp/dreamDroid-" + ts;
		}
		params.add(new BasicNameValuePair("filename", mFilename));

		Bundle args = new Bundle();
		args.putString("uri", URIStore.SCREENSHOT);
		args.putSerializable("params", params);
		getLoaderManager().restartLoader(0, args, (LoaderCallbacks<LoaderResult<byte[]>>) this);
	}


/*	public void performFileSearch() {

		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image*//*");
		startActivity(intent);
	}*/

	private void saveToFile() {
//		performFileSearch();

		if (mRawImage != null) {
			long timestamp = GregorianCalendar.getInstance().getTimeInMillis();

			File root = Environment.getExternalStorageDirectory();
			root = new File(String.format("%s%s%s", root.getAbsolutePath(), File.separator, "media/screenshots"));

			String extension = "";

			if (mFormat == FORMAT_JPG) {
				extension = "jpg";
			} else if (mFormat == FORMAT_PNG) {
				extension = "png";
			}

			String fileName = String.format("dreamDroid_%s.%s", timestamp, extension);
			FileOutputStream out;
			try {
				if (!root.exists()) {
					root.mkdirs();
				}

				File file = new File(root, fileName);
				file.createNewFile();
				out = new FileOutputStream(file);
				out.write(mRawImage);
				out.close();
				mScannerConn.scanFile(file.getAbsolutePath(), "image/*");
				showToast(getString(R.string.screenshot_saved, file.getAbsolutePath()));

			} catch (IOException e) {
				Log.e(DreamDroid.LOG_TAG, e.getLocalizedMessage());
				showToast(e.toString());
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Loader<LoaderResult<byte[]>> onCreateLoader(int id, Bundle args) {
		AsyncByteLoader loader = new AsyncByteLoader(getActionBarActivity(), args);
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<byte[]>> loader, LoaderResult<byte[]> result) {
		mHttpHelper.onLoadFinished();
		if (!result.isError()) {
			if (result.getResult().length > 0)
				onScreenshotAvailable(result.getResult());
			else
				showToast(getString(R.string.error));
		} else {
			showToast(result.getErrorText());
		}
	}

	@Override
	public void onLoaderReset(Loader<LoaderResult<byte[]>> loader) {
		mHttpHelper.onLoadFinished();
	}

	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getActionBarActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

}
