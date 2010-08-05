package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceInfo;
import android.app.ListActivity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.commonsware.cwac.merge.MergeAdapter;

public class DeviceInfoActivity extends ListActivity {
	private ExtendedHashMap mInfo;
	private AsyncTask<Void, String, Boolean> mGetInfoTask;
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

	private class GetDeviceInfoTask extends AsyncTask<Void, String, Boolean> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(Void... unused) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.device_info) + " - "
					+ getText(R.string.fetching_data));

			mInfo.clear();

			String xml = DeviceInfo.get(mShc);
			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.device_info) + " - "
						+ getText(R.string.parsing));

				if (DeviceInfo.parse(xml, mInfo)) {
					return true;
				}
			}
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			setTitle(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			String title = null;

			if (result) {
				title = getText(R.string.app_name) + "::" + getText(R.string.device_info);

				onInfoReady();
			} else {
				title = getText(R.string.app_name) + "::" + getText(R.string.device_info) + " - "
						+ getText(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			setTitle(title);

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.activities.AbstractHttpActivity#onCreate(android
	 * .os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setContentView(R.layout.device_info);
		mMerge = new MergeAdapter();
		mInfo = new ExtendedHashMap();
		mFrontends = new ArrayList<ExtendedHashMap>();
		mNics = new ArrayList<ExtendedHashMap>();
		mHdds = new ArrayList<ExtendedHashMap>();

		mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout fields = (LinearLayout) mInflater.inflate(R.layout.device_info, null);

		mMerge.addView(fields);

		mGuiVersion = (TextView) fields.findViewById(R.id.GuiVersion);
		mImageVersion = (TextView) fields.findViewById(R.id.ImageVersion);
		mInterfaceVersion = (TextView) fields.findViewById(R.id.InterfaceVersion);
		mFrontprocessorVersion = (TextView) fields.findViewById(R.id.FrontprocessorVersion);
		mDeviceName = (TextView) fields.findViewById(R.id.DeviceName);

		setClient();
		reload();
	}

	/**
	 * 
	 */
	private void setClient() {
		mShc = SimpleHttpClient.getInstance();
	}

	/**
	 * 
	 */
	private void reload() {
		if (mGetInfoTask != null) {
			mGetInfoTask.cancel(true);
		}

		mGetInfoTask = new GetDeviceInfoTask().execute();
	}

	/**
	 * @param id
	 * @return
	 */
	private TextView getListHeaderView(int id) {
		TextView header = (TextView) mInflater.inflate(R.layout.simple_header, null);
		header.setText(id);
		return header;
	}

	/**
	 * 
	 */
	private void setAdapter() {
		mFrontendAdapter = new SimpleAdapter(this, mFrontends, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.FRONTEND_NAME, DeviceInfo.FRONTEND_MODEL }, new int[] { android.R.id.text1,
				android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.frontends));
		mMerge.addAdapter(mFrontendAdapter);

		mNicAdapter = new SimpleAdapter(this, mNics, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.NIC_NAME, DeviceInfo.NIC_IP }, new int[] { android.R.id.text1, android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.nics));
		mMerge.addAdapter(mNicAdapter);

		mHddAdapter = new SimpleAdapter(this, mHdds, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.HDD_MODEL, DeviceInfo.HDD_CAPACITY }, new int[] { android.R.id.text1, android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.hdds));
		mMerge.addAdapter(mHddAdapter);
	}

	/**
	 * 
	 */
	@SuppressWarnings("unchecked")
	private void onInfoReady() {
		mFrontends = (ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.FRONTENDS);
		mNics = (ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.NICS);
		mHdds = (ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.HDDS);

		mGuiVersion.setText(mInfo.getString(DeviceInfo.GUI_VERSION));
		mImageVersion.setText(mInfo.getString(DeviceInfo.IMAGE_VERSION));
		mInterfaceVersion.setText(mInfo.getString(DeviceInfo.INTERFACE_VERSION));
		mFrontprocessorVersion.setText(mInfo.getString(DeviceInfo.FRONT_PROCESSOR_VERSION));
		mDeviceName.setText(mInfo.getString(DeviceInfo.DEVICE_NAME));

		if (getListAdapter() == null) {
			setAdapter();
			setListAdapter(mMerge);
		} else {
			mMerge.notifyDataSetChanged();
		}
	}

	private void showToast(String toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}
}
