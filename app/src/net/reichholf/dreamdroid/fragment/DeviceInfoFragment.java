/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceInfo;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;

/**
 * Shows device-specific information for the active profile.
 * 
 * @author sreichholf
 * 
 */
public class DeviceInfoFragment extends BaseHttpFragment {
	private ExtendedHashMap mInfo;
	private TextView mGuiVersion;
	private TextView mImageVersion;
	private TextView mInterfaceVersion;
	private TextView mFrontprocessorVersion;
	private TextView mDeviceName;
	private LinearLayout mFrontendsList;
	private LinearLayout mNicsList;
	private LinearLayout mHddsList;
	private ArrayList<ExtendedHashMap> mFrontends;
	private ArrayList<ExtendedHashMap> mNics;
	private ArrayList<ExtendedHashMap> mHdds;
	private LayoutInflater mInflater;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.device_info));

		if (savedInstanceState != null) {
			mInfo = savedInstanceState.getParcelable("info");
		} else {
			mInfo = new ExtendedHashMap();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mFrontends = new ArrayList<>();
		mNics = new ArrayList<>();
		mHdds = new ArrayList<>();

		mInflater = getLayoutInflater(savedInstanceState);
		View view = mInflater.inflate(R.layout.device_info, null);

		mGuiVersion = (TextView) view.findViewById(R.id.GuiVersion);
		mImageVersion = (TextView) view.findViewById(R.id.ImageVersion);
		mInterfaceVersion = (TextView) view.findViewById(R.id.InterfaceVersion);
		mFrontprocessorVersion = (TextView) view.findViewById(R.id.FrontprocessorVersion);
		mDeviceName = (TextView) view.findViewById(R.id.DeviceName);
		
		mFrontendsList = (LinearLayout) view.findViewById(R.id.FrontendsList);
		mNicsList = (LinearLayout) view.findViewById(R.id.NicsList);
		mHddsList = (LinearLayout) view.findViewById(R.id.HddsList);

		if (mInfo == null || mInfo.isEmpty()) {
			mReload = true;
		} else {
			onInfoReady();
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("info", mInfo);
	}

	/**
	 * Called when device info has been loaded and parsed successfully
	 */
	@SuppressWarnings("unchecked")
	private void onInfoReady() {
		mFrontends.clear();
		mFrontends.addAll((ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_FRONTENDS));

		mFrontendsList.removeAllViews();

		for (int i=0; i<mFrontends.size(); i++) {
			View item = mInflater.inflate(R.layout.two_line_list_item, null);
			
			TextView title = (TextView) item.findViewById(android.R.id.text1);
			title.setText((String) mFrontends.get(i).get(DeviceInfo.KEY_FRONTEND_NAME));
			
			TextView desc = (TextView) item.findViewById(android.R.id.text2);
			desc.setText((String) mFrontends.get(i).get(DeviceInfo.KEY_FRONTEND_MODEL));
			
			mFrontendsList.addView(item);
		}

		mNics.clear();
		mNics.addAll((ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_NICS));

		mNicsList.removeAllViews();

		for (int i=0; i<mNics.size(); i++) {
			View item = mInflater.inflate(R.layout.two_line_list_item, null);
			
			TextView title = (TextView) item.findViewById(android.R.id.text1);
			title.setText((String) mNics.get(i).get(DeviceInfo.KEY_NIC_NAME));
			
			TextView desc = (TextView) item.findViewById(android.R.id.text2);
			desc.setText((String) mNics.get(i).get(DeviceInfo.KEY_NIC_IP));
			
			mNicsList.addView(item);
		}

		mHdds.clear();
		mHdds.addAll((ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_HDDS));

		mHddsList.removeAllViews();

		for (int i=0; i<mHdds.size(); i++) {
			View item = mInflater.inflate(R.layout.two_line_list_item, null);
			
			TextView title = (TextView) item.findViewById(android.R.id.text1);
			title.setText((String) mHdds.get(i).get(DeviceInfo.KEY_HDD_MODEL));
			
			TextView desc = (TextView) item.findViewById(android.R.id.text2);
			desc.setText(String.format(getString(R.string.hdd_capacity),
					mHdds.get(i).get(DeviceInfo.KEY_HDD_CAPACITY),
					mHdds.get(i).get(DeviceInfo.KEY_HDD_FREE_SPACE)));
			
			mHddsList.addView(item);
		}

		mGuiVersion.setText(mInfo.getString(DeviceInfo.KEY_GUI_VERSION));
		mImageVersion.setText(mInfo.getString(DeviceInfo.KEY_IMAGE_VERSION));
		mInterfaceVersion.setText(mInfo.getString(DeviceInfo.KEY_INTERFACE_VERSION));
		mFrontprocessorVersion.setText(mInfo.getString(DeviceInfo.KEY_FRONT_PROCESSOR_VERSION));
		mDeviceName.setText(mInfo.getString(DeviceInfo.KEY_DEVICE_NAME));
	}

	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		return new AsyncSimpleLoader(getAppCompatActivity(), new DeviceInfoRequestHandler(), args);
	}

	/*
	 * You want override this if you don't override onLoadFinished!
	 */
	public void applyData(int loaderId, ExtendedHashMap content) {
		if (content != null) {
			mInfo.clear();
			mInfo.putAll(content);
			onInfoReady();
		} else {
			showToast(getText(R.string.not_available));
		}
	}
}
