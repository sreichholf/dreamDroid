/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;

import java.util.ArrayList;

/**
 * Check a profile for data-consistency and connectivity. Checks hostname, port,
 * connectivity & webinterface version
 * 
 * @author sre
 * 
 */
public class CheckProfile {
	public static final String LOG_TAG = "CheckProfile";

	public static final int[] FEATURE_EPGNOWNEXT_VERSION = { 1, 7, 0 };
	public static final int[] FEATURE_POST_REQEUEST = { 1, 7, 3 };

	public static final String KEY_HAS_ERROR = "error";
	public static final String KEY_SOFT_ERROR = "soft_error";
	public static final String KEY_VALUE = "value";
	public static final String KEY_ERROR_TEXT = "text";
	public static final String KEY_ERROR_TEXT_EXT = "text_ext";
	public static final String KEY_WHAT = "what";
	public static final String KEY_RESULT_LIST = "list";
	public static final int[] REQUIRED_VERSION = { 1, 6, 5 };
	@NonNull
	public static int[] CURRENT_VERSION = { 0, 0, 0 };

	/**
	 * @param profile
	 * @return
	 */
	@NonNull
	public static ExtendedHashMap checkProfile(@NonNull Profile profile, Context context) {
		CURRENT_VERSION = new int[]{ 0, 0, 0 };
		DreamDroid.disableSleepTimer();
		DreamDroid.disableNowNext();

		ArrayList<ExtendedHashMap> resultList = new ArrayList<>();
		ExtendedHashMap checkResult = new ExtendedHashMap();

		checkResult.put(KEY_RESULT_LIST, resultList);
		setError(checkResult, false, -1);

		String host = profile.getHost();

		if (host != null) {
			if (!host.contains(" ")) {
				addEntry(resultList, R.string.host, false, host);

				int port = profile.getPort();
				if (port > 0 && port <= 65535) {
					addEntry(resultList, R.string.port, false, Integer.toString(port));
					DeviceInfoRequestHandler dirh = new DeviceInfoRequestHandler();

					SimpleHttpClient shc = SimpleHttpClient.getInstance();
					String xml = profile.getCachedDeviceInfo();
					if(xml == null)
						xml = dirh.get(shc);

					if (xml != null && !shc.hasError()) {
						profile.setCachedDeviceInfo(xml);
						ExtendedHashMap deviceInfo = new ExtendedHashMap();

						if (dirh.parse(xml, deviceInfo)) {
							addEntry(resultList, R.string.device_name, false,
									deviceInfo.getString(DeviceInfo.KEY_DEVICE_NAME));

							String version = deviceInfo.getString(DeviceInfo.KEY_INTERFACE_VERSION, "0");
							int vc = checkVersion(version);
							if (vc >= 0) {
								int[] requiredForSleeptimer = { 1, 6, 5 };
								if (checkVersion(version, requiredForSleeptimer) >= 0)
									DreamDroid.enableSleepTimer();
								if (checkVersion(version, FEATURE_EPGNOWNEXT_VERSION) >= 0)
									DreamDroid.enableNowNext();
								if (checkVersion(version, FEATURE_POST_REQEUEST) >= 0)
									DreamDroid.setFeaturePostRequest(true);
								else
									DreamDroid.setFeaturePostRequest(false);

								addEntry(resultList, R.string.interface_version, false, version);
							} else {
								addEntry(resultList, R.string.interface_version, true, version,
										R.string.version_too_low);
								setError(checkResult, true, true, R.string.version_too_low);
							}
						} else {
							// TODO Parser-Error
						}

					} else if (shc.hasError()) {
						addEntry(resultList, R.string.connection, true, String.valueOf(host), R.string.connection_error, shc.getErrorText(context));
						setError(checkResult, true, R.string.connection_error, shc.getErrorText(context));
					} else if (xml == null) {
						// TODO Unexpected Error
					}
				} else {
					addEntry(resultList, R.string.port, true, String.valueOf(port), R.string.port_out_of_range);
					setError(checkResult, true, R.string.port_out_of_range);
				}
			} else {
				addEntry(resultList, R.string.host, true, String.valueOf(host), R.string.illegal_host);
				setError(checkResult, true, R.string.illegal_host);
			}
		}

		return checkResult;
	}

	public static int checkVersion(@NonNull String version) {
		return checkVersion(version, REQUIRED_VERSION);
	}

	public static int checkVersion(@NonNull String version, @NonNull int[] required) {
		String[] parts = version.split("\\.");

		for (int i = 0; i < required.length; i++) {
			int cur = 0;
			int req = required[i];

			if (parts.length >= i + 1) {
				try {
					cur = Integer.parseInt(parts[i]);
				} catch (NumberFormatException nfe) {
				}
			}

			if (cur == req) {
				if ((i + 1) == required.length) {
					return 0;
				}
			} else if (cur > req) {
				return 1;
			} else {
				return -1;
			}
		}

		return -1;
	}

	/**
	 * @param resultList
	 * @param checkTypeId
	 * @param hasError
	 * @param value
	 * @param errorTextId
	 */
	private static void addEntry(@NonNull ArrayList<ExtendedHashMap> resultList, int checkTypeId, boolean hasError,
								 String value, int errorTextId){
		addEntry(resultList, checkTypeId, hasError, value, errorTextId, null);
	}

	private static void addEntry(@NonNull ArrayList<ExtendedHashMap> resultList, int checkTypeId, boolean hasError,
								 String value, int errorTextId, String errorTextExt) {
		ExtendedHashMap entry = new ExtendedHashMap();
		entry.put(KEY_HAS_ERROR, hasError);
		entry.put(KEY_WHAT, checkTypeId);
		entry.put(KEY_VALUE, String.valueOf(value));
		entry.put(KEY_ERROR_TEXT, errorTextId);
		entry.put(KEY_ERROR_TEXT_EXT, errorTextExt);

		resultList.add(entry);
	}

	private static void addEntry(@NonNull ArrayList<ExtendedHashMap> resultList, int checkTypeId, boolean hasError, String value) {
		addEntry(resultList, checkTypeId, hasError, value, -1);
	}

	private static void setError(@NonNull ExtendedHashMap checkResult, boolean hasError, int errorTextId) {
		setError(checkResult, hasError, false, errorTextId, null);
	}

	private static void setError(@NonNull ExtendedHashMap checkResult, boolean hasError, int errorTextId, String extendedText) {
		setError(checkResult, hasError, false, errorTextId, extendedText);
	}

	private static void setError(@NonNull ExtendedHashMap checkResult, boolean hasError, boolean isSoftError, int errorTextId) {
		setError(checkResult, hasError, isSoftError, errorTextId, null);
	}

	private static void setError(@NonNull ExtendedHashMap checkResult, boolean hasError, boolean isSoftError, int errorTextId, @Nullable String extendedText) {
		checkResult.put(KEY_HAS_ERROR, hasError);
		checkResult.put(KEY_SOFT_ERROR, isSoftError);
		checkResult.put(KEY_ERROR_TEXT, errorTextId);
		if (extendedText == null)
			extendedText = "";
		checkResult.put(KEY_ERROR_TEXT_EXT, extendedText);

	}
}
