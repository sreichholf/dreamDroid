/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.loader.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.asynctask.GetDeviceInfoTask;
import net.reichholf.dreamdroid.enigma.DeviceInfo;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.device.DeviceInfoScreenKt;
import net.reichholf.dreamdroid.ui.device.DeviceInfoUiState;

/**
 * Shows device-specific information for the active profile.
 * Compose Material 3 UI; typed {@link DeviceInfo}.
 *
 * @author sreichholf
 *
 */
public class DeviceInfoFragment extends BaseHttpFragment
		implements GetDeviceInfoTask.GetDeviceInfoTaskHandler {
	@Nullable
	@State
	public DeviceInfo mInfo;

	@Nullable
	private GetDeviceInfoTask mDeviceInfoTask;

	private DeviceInfoUiState mUiState;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.device_info));
		mUiState = new DeviceInfoUiState();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.device_info, container, false);
		ComposeView compose = view.findViewById(R.id.compose_device_info);
		DeviceInfoScreenKt.bindDeviceInfoScreen(compose, mUiState);
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
			applyInfo(mInfo);
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

	private void applyInfo(@Nullable DeviceInfo info) {
		mUiState.apply(info, (capacity, free) ->
				String.format(getString(R.string.hdd_capacity), capacity, free));
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
			applyInfo(info);
		} else {
			applyInfo(null);
			if (errorText != null && !errorText.isEmpty()) {
				showToast(errorText);
			} else {
				showToast(getText(R.string.not_available));
			}
		}
	}
}
