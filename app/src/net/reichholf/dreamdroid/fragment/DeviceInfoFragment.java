/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.asynctask.GetDeviceInfoTask;
import net.reichholf.dreamdroid.enigma.DeviceHdd;
import net.reichholf.dreamdroid.enigma.DeviceFrontend;
import net.reichholf.dreamdroid.enigma.DeviceInfo;
import net.reichholf.dreamdroid.enigma.DeviceNic;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

/**
 * Shows device-specific information for the active profile.
 * Typed {@link DeviceInfo}; XML UI stays until device-info Compose.
 *
 * @author sreichholf
 *
 */
public class DeviceInfoFragment extends BaseHttpFragment
		implements GetDeviceInfoTask.GetDeviceInfoTaskHandler {
	@Nullable
	@State
	public DeviceInfo mInfo;

	private TextView mGuiVersion;
	private TextView mImageVersion;
	private TextView mInterfaceVersion;
	private TextView mFrontprocessorVersion;
	private TextView mDeviceName;
	private LinearLayout mFrontendsList;
	private LinearLayout mNicsList;
	private LinearLayout mHddsList;
	private LayoutInflater mInflater;

	@Nullable
	private GetDeviceInfoTask mDeviceInfoTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.device_info));
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = getLayoutInflater();
		View view = mInflater.inflate(R.layout.device_info, null);

		mGuiVersion = view.findViewById(R.id.GuiVersion);
		mImageVersion = view.findViewById(R.id.ImageVersion);
		mInterfaceVersion = view.findViewById(R.id.InterfaceVersion);
		mFrontprocessorVersion = view.findViewById(R.id.FrontprocessorVersion);
		mDeviceName = view.findViewById(R.id.DeviceName);

		mFrontendsList = view.findViewById(R.id.FrontendsList);
		mNicsList = view.findViewById(R.id.NicsList);
		mHddsList = view.findViewById(R.id.HddsList);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		boolean needReload = mInfo == null || mInfo.isEmpty();
		if (needReload) {
			mReload = true;
		}
		super.onViewCreated(view, savedInstanceState);
		if (!needReload) {
			onInfoReady(mInfo);
		}
	}

	@Override
	public void onDestroy() {
		if (mDeviceInfoTask != null) {
			mDeviceInfoTask.cancel(true);
			mDeviceInfoTask = null;
		}
		super.onDestroy();
	}

	private void onInfoReady(@NonNull DeviceInfo info) {
		mFrontendsList.removeAllViews();
		for (DeviceFrontend frontend : info.getFrontends()) {
			View item = mInflater.inflate(R.layout.two_line_list_item, null);
			TextView title = item.findViewById(android.R.id.text1);
			title.setText(frontend.getName());
			TextView desc = item.findViewById(android.R.id.text2);
			desc.setText(frontend.getModel());
			mFrontendsList.addView(item);
		}

		mNicsList.removeAllViews();
		for (DeviceNic nic : info.getNics()) {
			View item = mInflater.inflate(R.layout.two_line_list_item, null);
			TextView title = item.findViewById(android.R.id.text1);
			title.setText(nic.getName());
			TextView desc = item.findViewById(android.R.id.text2);
			desc.setText(nic.getIp());
			mNicsList.addView(item);
		}

		mHddsList.removeAllViews();
		for (DeviceHdd hdd : info.getHdds()) {
			View item = mInflater.inflate(R.layout.two_line_list_item, null);
			TextView title = item.findViewById(android.R.id.text1);
			title.setText(hdd.getModel());
			TextView desc = item.findViewById(android.R.id.text2);
			desc.setText(String.format(getString(R.string.hdd_capacity), hdd.getCapacity(), hdd.getFree()));
			mHddsList.addView(item);
		}

		mGuiVersion.setText(info.getGuiVersion());
		mImageVersion.setText(info.getImageVersion());
		mInterfaceVersion.setText(info.getInterfaceVersion());
		mFrontprocessorVersion.setText(info.getFrontProcessorVersion());
		mDeviceName.setText(info.getDeviceName());
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		// Unused: content comes from GetDeviceInfoTask / EnigmaClient.
		return new AsyncSimpleLoader(getAppCompatActivity(), new DeviceInfoRequestHandler(), args);
	}

	@Override
	public void applyData(int loaderId, @Nullable ExtendedHashMap content) {
		// Unused: content comes from GetDeviceInfoTask / EnigmaClient.
	}

	@Override
	protected void reload() {
		mReload = false;
		loadDeviceInfo();
	}

	private void loadDeviceInfo() {
		if (!isAdded()) {
			return;
		}
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (mDeviceInfoTask != null) {
			mDeviceInfoTask.cancel(true);
		}
		mDeviceInfoTask = new GetDeviceInfoTask(this);
		mDeviceInfoTask.execute();
	}

	@Override
	public void onDeviceInfoReady(boolean success, @Nullable DeviceInfo info, @Nullable String errorText) {
		if (!isAdded()) {
			return;
		}
		mHttpHelper.onLoadFinished();
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (success && info != null) {
			mInfo = info;
			onInfoReady(info);
		} else {
			if (errorText != null && !errorText.isEmpty()) {
				showToast(errorText);
			} else {
				showToast(getText(R.string.not_available));
			}
		}
	}
}
