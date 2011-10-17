/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import net.reichholf.dreamdroid.Profile;
import android.util.Log;
/**
 * @author sre
 *
 */
public class DeviceDetector {
	public static String LOG_TAG = DeviceDetector.class.getName();
	public static final String[] KNOWN_HOSTNAMES = { "dm500hd", "dm800", "dm800se",  "dm7020hd", "dm7025", "dm8000" };
	
	public static ArrayList<Profile> getAvailableHosts(){
		ArrayList<Profile> profiles = new ArrayList<Profile>();
		for(String hostname : KNOWN_HOSTNAMES){
			try {
				InetAddress.getByName(hostname);
				boolean simpleRemote = false;
				if(!hostname.equals("dm8000") && !hostname.equals("dm7020hd")){
					simpleRemote = true;
				}
				
				Profile p = new Profile();
				p.setName(hostname);
				p.setHost(hostname);
				p.setPort(80);
				p.setUser("root");
				p.setSimpleRemote(simpleRemote);
				profiles.add(p);
			} catch (UnknownHostException e) {
				Log.e(LOG_TAG, e.getMessage());
			}
		}
		
		JmDNS jmdns;
		try {
			jmdns = JmDNS.create();
			ServiceInfo si[] = jmdns.list("_http._tcp.local.");
			for(ServiceInfo s : si){
				Log.i(LOG_TAG, s.getHostAddresses().toString());
				if(s.getName().toLowerCase().matches("dm[0-9]{1,4}.*") ){
					String address = s.getHostAddress();
					int port = s.getPort();
					boolean simpleRemote = false;
					if(!s.getName().toLowerCase().contains("dm8000") && !s.getName().toLowerCase().contains("dm7020hd")){
						simpleRemote = true;
					}
					
					Profile p = new Profile();
					p.setName(s.getName());
					p.setHost(address);
					p.setPort(port);
					p.setUser("root");
					p.setSimpleRemote(simpleRemote);
					profiles.add(p);
				}
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, e.getMessage());
		}
		
		return profiles;
	}
}
