/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

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

	public static ExtendedHashMap checkProfile(Profile profile) {
		ArrayList<ExtendedHashMap> resultList = new ArrayList<ExtendedHashMap>();
		ExtendedHashMap checkResult = new ExtendedHashMap();
		
		checkResult.put(KEY_RESULT_LIST, resultList);
		setError(checkResult, false);
		
		SimpleHttpClient shc = SimpleHttpClient.getInstance();
		String host = profile.getHost();
		
		if (host != null) {
			if (!host.contains(" ")) {
				addEntry(resultList, "Hostname/IP", false, host);

				int port = profile.getPort();

				if (port > 0 && port <= 65535) {
					addEntry(resultList, "Port", false, Integer.toString(port));

					String xml = DeviceInfo.get(shc);

					if (xml != null && !shc.hasError()) {
						ExtendedHashMap deviceInfo = new ExtendedHashMap();

						if (DeviceInfo.parse(xml, deviceInfo)) {
							addEntry(resultList, "DeviceName", false, deviceInfo.getString(DeviceInfo.DEVICE_NAME));
							
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
									addEntry(resultList, "Interface Version", false, version);
								} else {
									addEntry(resultList, "Interface Version", true, version, "Version < 1.6");									
									setError(checkResult, true);
								}

							}
						} else {
							// TODO Parser-Error
						}

					} else if (shc.hasError()) {
						addEntry(resultList, "HTTP-Acces", true, String.valueOf(host), shc.getErrorText());
						setError(checkResult, true);
					} else if (xml == null) {
						// TODO Unexpected Error
					}
				} else {
					addEntry(resultList, "Port", true, String.valueOf(port), "Port not between 1 and 65535");
					setError(checkResult, true);
				}
			} else {
				addEntry(resultList, "Hostname/IP", true, String.valueOf(host), "Illegal hostname");
				setError(checkResult, true);
			}
		}

		return checkResult;
	}
	
	private static void addEntry(ArrayList<ExtendedHashMap> resultList, String checkName, boolean hasError, String value, String errorText){
		ExtendedHashMap entry = new ExtendedHashMap();
		entry.put(KEY_HAS_ERROR, hasError);
		entry.put(KEY_WHAT, checkName);
		entry.put(KEY_VALUE, String.valueOf(value) );
		entry.put(KEY_ERROR_TEXT, errorText);
		
		resultList.add(entry);
	}
	
	private static void addEntry(ArrayList<ExtendedHashMap> resultList, String checkName, boolean hasError, String value){
		addEntry(resultList, checkName, hasError, value, "");
	}
	
	private static void setError(ExtendedHashMap checkResult, boolean hasError){
		checkResult.put(KEY_HAS_ERROR, hasError);
	}
}
