/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

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
	
	public static final String KEY_VERSION = "version";
	public static final String KEY_HOST = "host";
	public static final String KEY_PORT = "port";
	public static final String KEY_CONNECTIVITY = "connectivity";
	public static final String KEY_DEVICE_NAME = "devicename";

	public static final String KEY_RESULT = "result";
	public static final String KEY_RESULT_TEXT = "text";

	public static ExtendedHashMap checkProfile(Profile profile) {
		ExtendedHashMap checkResult = new ExtendedHashMap();

		SimpleHttpClient shc = SimpleHttpClient.getInstance();
		String host = profile.getHost();

		if (host != null) {
			if (!host.contains(" ")) {
				checkResult.put(KEY_HOST, new ExtendedHashMap().put(KEY_RESULT, true));

				int port = profile.getPort();

				if (port > 0 && port <= 65535) {
					checkResult.put(KEY_PORT, new ExtendedHashMap().put(KEY_RESULT, true));

					String xml = DeviceInfo.get(shc);

					if (xml != null && !shc.hasError()) {
						ExtendedHashMap deviceInfo = new ExtendedHashMap();

						if (DeviceInfo.parse(xml, deviceInfo)) {
							checkResult.put(
									KEY_DEVICE_NAME,
									new ExtendedHashMap().put(KEY_RESULT_TEXT,
											deviceInfo.getString(DeviceInfo.DEVICE_NAME)));

							String version = deviceInfo.getString(DeviceInfo.INTERFACE_VERSION);
							String[] v = version.split(".");

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
									checkResult.put(KEY_VERSION, new ExtendedHashMap().put(KEY_RESULT, true));
								} else {
									ExtendedHashMap map = new ExtendedHashMap();
									map.put(KEY_RESULT, false);
									map.put(KEY_RESULT_TEXT, "Version < 1.6");
									checkResult.put(KEY_VERSION, map);
								}

							}
						} else {
							// TODO Parser-Error
						}

					} else if (shc.hasError()) {
						ExtendedHashMap map = new ExtendedHashMap();
						map.put(KEY_RESULT, false);
						map.put(KEY_RESULT_TEXT, shc.getErrorText());

						checkResult.put(KEY_CONNECTIVITY, map);
					} else if (xml == null) {
						// TODO Unexpected Error
					}
				} else {
					checkResult.put(KEY_PORT, false);
				}
			} else {
				checkResult.put(KEY_HOST, false);
			}
		}

		return checkResult;
	}
}
