/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceInfo;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;
import android.app.Activity;
import android.app.Dialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.ScrollView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.commonsware.cwac.merge.MergeAdapter;

/**
 * Shows device-specific information for the active profile.
 *
 * @author sreichholf
 *
 */
public class DeviceInfoFragment extends SherlockListFragment implements ActivityCallbackHandler {
	private ExtendedHashMap mInfo;
	private GetDeviceInfoTask mGetInfoTask;
	private MergeAdapter mMerge;
	private SimpleAdapter mFrontendAdapter;
	private SimpleAdapter mNicAdapter;
	private SimpleAdapter mHddAdapter;
	private TextView mGuiVersion;
	private TextView mImageVersion;
	private TextView mInterfaceVersion;
	private TextView mFrontprocessorVersion;
	private TextView mDeviceName;
	private ArrayList<ExtendedHashMap> mFrontends;
	private ArrayList<ExtendedHashMap> mNics;
	private ArrayList<ExtendedHashMap> mHdds;
	private SimpleHttpClient mShc;
	private LayoutInflater mInflater;

	private Activity mActivity;

	/**
	 * <code>AsyncTask</code> to Fetch the device information async.
	 *
	 * @author sre
	 *
	 */
	private class GetDeviceInfoTask extends AsyncTask<Void, String, Boolean> {

		@Override
		protected Boolean doInBackground(Void... unused) {
			publishProgress(getString(R.string.fetching_data));

			mInfo.clear();
			DeviceInfoRequestHandler handler = new DeviceInfoRequestHandler();
			String xml = handler.get(mShc);
			if (xml != null) {
				publishProgress(getString(R.string.parsing));

				if (handler.parse(xml, mInfo)) {
					return true;
				}
			}
			return false;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			mActivity.setTitle(progress[0]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			String title = null;

			if (result) {
				title = getString(R.string.device_info);
				onInfoReady();
			} else {
				title = getString(R.string.get_content_error);
				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			mActivity.setTitle(title);

		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivity = getSherlockActivity();
		mActivity.setProgressBarIndeterminateVisibility(false);
		getSherlockActivity().setTitle(getText(R.string.device_info));
		
		mMerge = new MergeAdapter();
		mInfo = new ExtendedHashMap();
		mFrontends = new ArrayList<ExtendedHashMap>();
		mNics = new ArrayList<ExtendedHashMap>();
		mHdds = new ArrayList<ExtendedHashMap>();

		mInflater = getLayoutInflater(savedInstanceState);
		ScrollView fields = (ScrollView) mInflater.inflate(R.layout.device_info, null);

		mMerge.addView(fields);

		mGuiVersion = (TextView) fields.findViewById(R.id.GuiVersion);
		mImageVersion = (TextView) fields.findViewById(R.id.ImageVersion);
		mInterfaceVersion = (TextView) fields.findViewById(R.id.InterfaceVersion);
		mFrontprocessorVersion = (TextView) fields.findViewById(R.id.FrontprocessorVersion);
		mDeviceName = (TextView) fields.findViewById(R.id.DeviceName);
		setClient();

		if(savedInstanceState == null){
			reload();
		} else {
			mInfo = (ExtendedHashMap) savedInstanceState.getParcelable("info");
			onInfoReady();
		}
	}

	@Override
	public void onDestroy(){
		if(mGetInfoTask != null)
			mGetInfoTask.cancel(true);
		super.onDestroy();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("info", mInfo);
	}

	/**
	 * Instanciates a <code>SimpleHttpClient</code> for execution of
	 * http-requests
	 */
	private void setClient() {
		mShc = SimpleHttpClient.getInstance();
	}

	/**
	 * Reloads the device information
	 */
	private void reload() {
		if (mGetInfoTask != null) {
			mGetInfoTask.cancel(true);
		}

		mGetInfoTask = new GetDeviceInfoTask();
		mGetInfoTask.execute();
	}

	/**
	 * @param id
	 *            ID of the R.string to be set as the content of the
	 *            <code>DreamDroid.R.layout.simple_header</code>
	 * @return
	 */
	private TextView getListHeaderView(int id) {
		TextView header = (TextView) mInflater.inflate(R.layout.simple_header, null);
		header.setText(id);
		return header;
	}

	/**
	 * Set all required stuff for the
	 * <code>com.commonsware.cwac.merge.MergeAdapter</code>
	 */
	private void setAdapter() {
		mFrontendAdapter = new SimpleAdapter(mActivity, mFrontends, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.KEY_FRONTEND_NAME, DeviceInfo.KEY_FRONTEND_MODEL }, new int[] { android.R.id.text1,
				android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.frontends));
		mMerge.addAdapter(mFrontendAdapter);

		mNicAdapter = new SimpleAdapter(mActivity, mNics, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.KEY_NIC_NAME, DeviceInfo.KEY_NIC_IP }, new int[] { android.R.id.text1, android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.nics));
		mMerge.addAdapter(mNicAdapter);

		mHddAdapter = new SimpleAdapter(mActivity, mHdds, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.KEY_HDD_MODEL, DeviceInfo.KEY_HDD_CAPACITY }, new int[] { android.R.id.text1, android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.hdds));
		mMerge.addAdapter(mHddAdapter);
	}

	/**
	 * Called when device info has been loaded and parsed successfully
	 */
	@SuppressWarnings("unchecked")
	private void onInfoReady() {
		mFrontends = (ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_FRONTENDS);
		mNics = (ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_NICS);
		mHdds = (ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_HDDS);

		mGuiVersion.setText(mInfo.getString(DeviceInfo.KEY_GUI_VERSION));
		mImageVersion.setText(mInfo.getString(DeviceInfo.KEY_IMAGE_VERSION));
		mInterfaceVersion.setText(mInfo.getString(DeviceInfo.KEY_INTERFACE_VERSION));
		mFrontprocessorVersion.setText(mInfo.getString(DeviceInfo.KEY_FRONT_PROCESSOR_VERSION));
		mDeviceName.setText(mInfo.getString(DeviceInfo.KEY_DEVICE_NAME));

		if (getListAdapter() == null) {
			setAdapter();
			setListAdapter(mMerge);
		} else {
			mMerge.notifyDataSetChanged();
		}
	}

	/**
	 * Shows a toast message
	 *
	 * @param toastText
	 *            The text to set for the toast
	 */
	private void showToast(String toastText) {
		Toast toast = Toast.makeText(mActivity, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public Dialog onCreateDialog(int id) {
		return null;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}
}
