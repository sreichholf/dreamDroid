/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

/**
 * Check a profile for data-consistency and connectivity. Checks hostname, port,
 * connectivity & webinterface version
 * 
 * @author sre
 * 
 */
public class CheckProfile {
	public static final String KEY_HAS_ERROR = "error";
	public static final String KEY_VALUE = "value";
	public static final String KEY_ERROR_TEXT = "text";
	public static final String KEY_WHAT = "what";
	public static final String KEY_RESULT_LIST = "list";

	/**
	 * @param profile
	 * @return
	 */
	public static ExtendedHashMap checkProfile(Profile profile) {
		ArrayList<ExtendedHashMap> resultList = new ArrayList<ExtendedHashMap>();
		ExtendedHashMap checkResult = new ExtendedHashMap();

		checkResult.put(KEY_RESULT_LIST, resultList);
		setError(checkResult, false, -1);

		SimpleHttpClient shc = SimpleHttpClient.getInstance();
		String host = profile.getHost();

		if (host != null) {
			if (!host.contains(" ")) {
				addEntry(resultList, R.string.host, false, host);

				int port = profile.getPort();

				if (port > 0 && port <= 65535) {
					addEntry(resultList, R.string.port, false, Integer.toString(port));

					String xml = DeviceInfo.get(shc);

					if (xml != null && !shc.hasError()) {
						ExtendedHashMap deviceInfo = new ExtendedHashMap();

						if (DeviceInfo.parse(xml, deviceInfo)) {
							addEntry(resultList, R.string.device_name, false,
									deviceInfo.getString(DeviceInfo.DEVICE_NAME));

							String version = deviceInfo.getString(DeviceInfo.INTERFACE_VERSION);
							String[] v = version.split("\\.");

							if (v.length >= 2) {
								String major = v[0];
								String minor = v[1];

								int majorVersion = 0;
								int minorVersion = 0;

								try {
									majorVersion = Integer.parseInt(major);
									minorVersion = Integer.parseInt(minor);
								} catch (NumberFormatException nfe) {
									try {
										minorVersion = Integer.parseInt(minor.substring(0, 1));
									} catch (NumberFormatException nfex) {
										// TODO Handle final nfe for minor
									}
								}

								if (majorVersion > 1 || (majorVersion == 1 && minorVersion >= 6)) {
									addEntry(resultList, R.string.interface_version, false, version);
								} else {
									addEntry(resultList, R.string.interface_version, true, version,
											R.string.version_too_low);
									setError(checkResult, true, R.string.version_too_low);
								}

							}
						} else {
							// TODO Parser-Error
						}

					} else if (shc.hasError()) {
						addEntry(resultList, R.string.host, true, String.valueOf(host), R.string.connection_error);
						setError(checkResult, true, R.string.connection_error);
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

	/**
	 * @param resultList
	 * @param checkTypeId
	 * @param hasError
	 * @param value
	 * @param errorTextId
	 */
	private static void addEntry(ArrayList<ExtendedHashMap> resultList, int checkTypeId, boolean hasError,
			String value, int errorTextId) {
		ExtendedHashMap entry = new ExtendedHashMap();
		entry.put(KEY_HAS_ERROR, hasError);
		entry.put(KEY_WHAT, checkTypeId);
		entry.put(KEY_VALUE, String.valueOf(value));
		entry.put(KEY_ERROR_TEXT, errorTextId);

		resultList.add(entry);
	}

	/**
	 * @param resultList
	 * @param checkTypeId
	 * @param hasError
	 * @param value
	 */
	private static void addEntry(ArrayList<ExtendedHashMap> resultList, int checkTypeId, boolean hasError, String value) {
		addEntry(resultList, checkTypeId, hasError, value, -1);
	}

	/**
	 * @param checkResult
	 * @param hasError
	 */
	private static void setError(ExtendedHashMap checkResult, boolean hasError, int errorTextId) {
		checkResult.put(KEY_HAS_ERROR, hasError);
		checkResult.put(KEY_ERROR_TEXT, errorTextId);
	}
}
