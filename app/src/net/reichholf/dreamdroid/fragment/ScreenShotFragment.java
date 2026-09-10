/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.FileProvider;

import com.evernote.android.state.State;

import kotlinx.coroutines.Job;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.enigma.ScreenshotLoadKt;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotScreenKt;
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotUiState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * Allows fetching and showing the actual TV-Screen content.
 * Phone UI is Compose Material 3 with PhotoView zoom via AndroidView.
 *
 * @author sre
 */
public class ScreenShotFragment extends BaseFragment {
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

	private boolean mSetTitle;
	private boolean mActionsEnabled;
	private int mType;
	private int mFormat;
	private int mSize;
	private String mFilename;
	@State
	public byte[] mRawImage;
	@Nullable
	private MediaScannerConnection mScannerConn;
	private HttpFragmentHelper mHttpHelper;
	private ScreenshotUiState mUiState;
	@Nullable
	private Job mLoadJob;

	@Override
	public boolean hasHeader() {
		return false;
	}

	private class DummyMediaScannerConnectionClient implements MediaScannerConnectionClient {
		@Override
		public void onMediaScannerConnected() {
		}

		@Override
		public void onScanCompleted(String arg0, Uri arg1) {
		}

	}

	public ScreenShotFragment() {
		super();
		shouldRetain(true);
		mHttpHelper = new HttpFragmentHelper();
		mActionsEnabled = true;
		mSetTitle = true;
	}

	public ScreenShotFragment(boolean retainInstance, boolean actionsEnabled, boolean setTitle) {
		super();
		shouldRetain(retainInstance);
		mActionsEnabled = actionsEnabled;
		mSetTitle = setTitle;
	}

	private void shouldRetain(boolean retainInstance) {
		Bundle args = new Bundle();
		args.putBoolean(BUNDLE_KEY_RETAIN, retainInstance);
		setArguments(args);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mShouldRetainInstance = getArguments().getBoolean(BUNDLE_KEY_RETAIN);
		super.onCreate(savedInstanceState);
		if (mHttpHelper == null)
			mHttpHelper = new HttpFragmentHelper(this);
		else
			mHttpHelper.bindToFragment(this);

		setHasOptionsMenu(true);
		if (mSetTitle)
			initTitles(getString(R.string.screenshot));
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getAppCompatActivity().setTitle(getText(R.string.screenshot));
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Bundle extras = getArguments();

		if (extras == null) {
			extras = new Bundle();
		}

		mType = extras.getInt(KEY_TYPE, TYPE_ALL);
		mFormat = extras.getInt(KEY_FORMAT, mType == TYPE_OSD ? FORMAT_PNG : FORMAT_JPG);
		mSize = extras.getInt(KEY_SIZE, -1);
		mFilename = extras.getString(KEY_FILENAME);

		if (mRawImage == null) {
			mRawImage = new byte[0];
		}

		mUiState = new ScreenshotUiState();
		mUiState.setActionsEnabled(mActionsEnabled);
		if (mRawImage.length > 0) {
			mUiState.setBitmap(BitmapFactory.decodeByteArray(mRawImage, 0, mRawImage.length));
		}

		ComposeView composeView = new ComposeView(requireContext());
		composeView.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
		));
		ScreenshotScreenKt.bindScreenshotScreen(
				composeView,
				mUiState,
				() -> {
					reload();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					share();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					saveToFile();
					return kotlin.Unit.INSTANCE;
				}
		);
		return composeView;
	}

	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mHttpHelper.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		mScannerConn = new MediaScannerConnection(getAppCompatActivity(), new DummyMediaScannerConnectionClient());
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
		mScannerConn = null;
		cancelLoad();
		super.onPause();
	}

	@Override
	public void createOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		if (menu.findItem(R.id.menu_reload) == null && mActionsEnabled) {
			inflater.inflate(R.menu.reload, menu);
			inflater.inflate(R.menu.save, menu);
			inflater.inflate(R.menu.share, menu);
		}
	}

	private void share() {
		File file = saveToFile(true);
		if (file == null) {
			showToast(getString(R.string.error));
			return;
		}
		file.setReadable(true, false);
		Uri uri = FileProvider.getUriForFile(getContext(), getContext().getApplicationContext().getPackageName() + ".provider", file);
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_STREAM, uri);
		intent.setType(String.format("image/%s", getMimeType()));
		startActivity(Intent.createChooser(intent, null));
	}

	@NonNull
	private String getFileExtension() {
		if (mFormat == FORMAT_JPG) {
			return "jpg";
		} else if (mFormat == FORMAT_PNG) {
			return "png";
		}
		return "";
	}

	private String getMimeType() {
		if (mFormat == FORMAT_JPG) {
			return "jpeg";
		} else if (mFormat == FORMAT_PNG) {
			return "png";
		}
		return "";
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (mScannerConn != null)
			mScannerConn.disconnect();
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case Statics.ITEM_RELOAD:
				reload();
				break;
			case Statics.ITEM_SAVE:
				saveToFile();
				break;
			case R.id.menu_share:
				share();
				break;
		}

		return true;
	}

	/**
	 * @param bytes
	 */
	private void onScreenshotAvailable(@NonNull byte[] bytes) {
		if (!isAdded())
			return;
		mRawImage = bytes;
		if (mUiState != null) {
			mUiState.setBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
			mUiState.setLoading(false);
		}
	}

	public void reload() {
		if (mUiState != null) {
			mUiState.setLoading(true);
		}
		mHttpHelper.onLoadStarted();
		ArrayList<NameValuePair> params = new ArrayList<>();

		switch (mType) {
			case (TYPE_OSD):
				params.add(new NameValuePair("o", " "));
				params.add(new NameValuePair("n", " "));
				break;
			case (TYPE_VIDEO):
				params.add(new NameValuePair("v", " "));
				break;
			case (TYPE_ALL):
				break;
		}

		switch (mFormat) {
			case (FORMAT_JPG):
				params.add(new NameValuePair("format", "jpg"));
				break;
			case (FORMAT_PNG):
				params.add(new NameValuePair("format", "png"));
				break;
		}

		if (mSize > 0)
			params.add(new NameValuePair("r", String.valueOf(mSize)));

		long ts = (new GregorianCalendar().getTimeInMillis()) / 1000;
		mFilename = "/tmp/dreamDroid-" + ts;

		params.add(new NameValuePair("filename", mFilename));

		cancelLoad();
		mLoadJob = ScreenshotLoadKt.launchScreenshotLoad(this, params, (success, bytes, errorText) -> {
			onScreenshotLoadFinished(success, bytes, errorText);
			return kotlin.Unit.INSTANCE;
		});
	}

	private void cancelLoad() {
		if (mLoadJob != null) {
			mLoadJob.cancel(null);
			mLoadJob = null;
		}
	}

	private void onScreenshotLoadFinished(boolean success, @Nullable byte[] bytes, @Nullable String errorText) {
		if (!isAdded()) {
			return;
		}
		mHttpHelper.onLoadFinished();
		if (mUiState != null) {
			mUiState.setLoading(false);
		}
		if (success && bytes != null && bytes.length > 0) {
			onScreenshotAvailable(bytes);
		} else if (errorText != null && !errorText.isEmpty()) {
			showToast(errorText);
		} else {
			showToast(getString(R.string.error));
		}
	}

	private void saveToFile() {
		saveToFile(false);
	}

	@Nullable
	private File saveToFile(boolean inCache) {
		if (mRawImage != null) {
			String extension = getFileExtension();
			String fileName;
			if (inCache) {
				fileName = String.format("dreamDroid.%s", extension);
				try {
					File file = new File(getContext().getCacheDir(), fileName);
					FileOutputStream out = new FileOutputStream(file);
					out.write(mRawImage);
					out.close();
					return file;
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
			}
			long timestamp = GregorianCalendar.getInstance().getTimeInMillis();
			fileName = String.format("dreamDroid_%s.%s", timestamp, extension);
			ContentValues imageDetails = new ContentValues();
			imageDetails.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);

			Uri imageCollection;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				imageCollection = MediaStore.Images.Media
						.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
			} else {
				imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
			}

			FileOutputStream out;
			ContentResolver resolver = getAppCompatActivity().getApplicationContext().getContentResolver();
			Uri imageContentUri = resolver.insert(imageCollection, imageDetails);
			try {
				ParcelFileDescriptor pfd = resolver.openFileDescriptor(imageContentUri, "w");
				out = new FileOutputStream(pfd.getFileDescriptor());
				out.write(mRawImage);
				out.close();
				pfd.close();
				showToast(getString(R.string.screenshot_saved, fileName));
			} catch (IOException e) {
				Log.e(DreamDroid.LOG_TAG, e.getLocalizedMessage());
				showToast(e.toString());
			}
		}
		return null;
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

	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

}
