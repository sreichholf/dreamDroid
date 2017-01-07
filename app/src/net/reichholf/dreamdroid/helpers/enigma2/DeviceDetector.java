/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import android.util.Log;

import net.reichholf.dreamdroid.Profile;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

/**
 * @author sre
 * 
 */
public class DeviceDetector {
	public static String LOG_TAG = DeviceDetector.class.getName();
	public static final String[] KNOWN_HOSTNAMES = { "dm500hd", "dm800", "dm800se", "dm7020hd", "dm7025", "dm8000", "dm800sev2", "dm500hdsev2", "dm7020hdv2", "dm7080", "dm820", "dm520", "dm525", "dm900" };

	public static ArrayList<Profile> getAvailableHosts() {
		ArrayList<Profile> profiles = new ArrayList<>();
		for (String hostname : KNOWN_HOSTNAMES) {
			try {
				InetAddress host = InetAddress.getByName(hostname);
				if (!host.isReachable(1500))
					continue;
				boolean simpleRemote = false;
				String ip = host.getHostAddress();

				Profile p = Profile.getDefault();
				p.setName(hostname);
				p.setHost(ip);
				p.setStreamHost(ip);
				p.setPort(80);
				p.setUser("root");
				p.setSimpleRemote(simpleRemote);
				addToList(profiles, p);
			} catch (IOException e) {
				Log.w(LOG_TAG, e.getMessage());
			}
		}

		JmDNS jmdns;
		try {
			jmdns = JmDNS.create();
			ServiceInfo si[] = jmdns.list("_http._tcp.local.");
			for (ServiceInfo s : si) {
				Log.i(LOG_TAG, Arrays.toString(s.getHostAddresses()));
				if (s.getName().toLowerCase(Locale.US).matches("dm[0-9]{1,4}.*")) {
					String address = s.getHostAddresses()[0];
					int port = s.getPort();
					boolean simpleRemote = false;
					Profile p = Profile.getDefault();
					p.setName(s.getName());
					p.setHost(address);
					p.setStreamHost(address);
					p.setPort(port);
					p.setUser("root");
					p.setSimpleRemote(simpleRemote);
					addToList(profiles, p);
				}
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, e.getMessage());
		}

		return profiles;
	}

	private static void addToList(ArrayList<Profile> list, Profile profile) {
		for (Profile p : list) {
			if (profile.getHost().equals(p.getHost())) {
				list.remove(p);
				break;
			}
		}
		list.add(profile);
	}
}
