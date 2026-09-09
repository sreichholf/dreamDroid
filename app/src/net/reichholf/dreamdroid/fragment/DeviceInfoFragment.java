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
import net.reichholf.dreamdroid.enigma.DeviceInfo;
import net.reichholf.dreamdroid.enigma.DeviceInfoLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.device.DeviceInfoScreenKt;
import net.reichholf.dreamdroid.ui.device.DeviceInfoUiState;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Shows device-specific information for the active profile.
 * Compose Material 3 UI; typed {@link DeviceInfo} via coroutine + {@code EnigmaClient}.
 *
 * @author sreichholf
 *
 */
public class DeviceInfoFragment extends BaseHttpFragment {
	@Nullable
	@State
	public DeviceInfo mInfo;

	@Nullable
	private Job mLoadJob;

	private DeviceInfoUiState mUiState;
	private boolean mDeviceInfoReady;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.device_info));
		mUiState = new DeviceInfoUiState();
		mDeviceInfoReady = false;
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
		// Use needReload, not mReload: reload() clears mReload while the load is still in flight.
		if (!needReload) {
			mDeviceInfoReady = true;
			applyInfo(mInfo);
		}
	}

	@Override
	public void onDestroyView() {
		cancelLoad();
		super.onDestroyView();
	}

	private void cancelLoad() {
		if (mLoadJob != null) {
			mLoadJob.cancel(null);
			mLoadJob = null;
		}
	}

	private void applyInfo(@Nullable DeviceInfo info) {
		mUiState.apply(info, (capacity, free) ->
				String.format(getString(R.string.hdd_capacity), capacity, free));
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		// Unused: content comes from EnigmaClient coroutines.
		return new AsyncSimpleLoader(getAppCompatActivity(), new DeviceInfoRequestHandler(), args);
	}

	@Override
	public void applyData(int loaderId, @Nullable ExtendedHashMap content) {
		// Unused: content comes from EnigmaClient coroutines.
	}

	@Override
	protected void reload() {
		mReload = false;
		loadDeviceInfo();
	}

	private void loadDeviceInfo() {
		if (!isAdded() || getView() == null) {
			return;
		}
		if (!mDeviceInfoReady) {
			mUiState.beginLoading();
		}
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		cancelLoad();
		mLoadJob = DeviceInfoLoadKt.launchDeviceInfoLoad(this, getHttpClient(), (success, info, errorText) -> {
			onDeviceInfoReady(success, info, errorText);
			return Unit.INSTANCE;
		});
	}

	private void onDeviceInfoReady(boolean success, @Nullable DeviceInfo info, @Nullable String errorText) {
		if (!isAdded()) {
			return;
		}
		mHttpHelper.onLoadFinished();
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (!success || info == null) {
			if (!mDeviceInfoReady) {
				applyInfo(null);
			}
			if (errorText != null && !errorText.isEmpty()) {
				showToast(errorText);
			} else {
				showToast(getText(R.string.not_available));
			}
			return;
		}
		mDeviceInfoReady = true;
		mInfo = info;
		applyInfo(info);
	}
}
