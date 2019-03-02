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
	public static final String[] KNOWN_HOSTNAMES = { "triplex", "ultrabox", "azboxhd", "azboxme", "azboxminime", "beyonwizv2", "sogno8800hd", "uniboxhde", "bre2zet2c", "sf128", "sf138", "sf3038", "9910lx", "9911lx", "9920lx", "e4hd", "e4hdcombo", "e4hdhybrid", "e4hdultra", "odin2hybrid", "odinplus", "protek4k", "sf208", "sf228", "sf238", "singleboxlcd", "twinboxlcd", "twinboxlcdci5", "cc1", "cube", "force1", "force1plus", "force2", "force2nano", "force2plus", "force2plushv", "force2se", "force3uhd", "force3uhdplus", "force4", "iqonios100hd", "iqonios200hd", "iqonios300hd", "iqonios300hdv2", "mediabox", "optimussos", "optimussos1", "optimussos1plus", "optimussos2", "optimussos2plus", "optimussos3plus", "tm2t", "tm4ksuper", "tmnano", "tmnano2super", "tmnano2t", "tmnano3t", "tmnanom3", "tmnanose", "tmnanosecombo", "tmnanosem2", "tmnanoseplus", "tmsingle", "tmtwin", "tmtwin4k", "valalinux", "worldvisionf1", "worldvisionf1plus", "anadol4k", "anadol4kcombo", "anadol4kv2", "axashis4kcombo", "axashis4kcomboplus", "dinobot4k", "dinobot4kl", "dinobot4kmini", "dinobot4kplus", "dinobot4kpro", "dinobot4kse", "dinoboth265", "dinobotu55", "ferguson4k", "mediabox4k", "dm500hd", "dm500hdv2", "dm520", "dm7020hd", "dm7020hdv2", "dm7080", "dm800", "dm8000", "dm800se", "dm800sev2", "dm820", "dm900", "dm920", "ebox5000", "ebox5100", "ebox7358", "eboxlumi", "bre2ze", "enfinity", "evomini", "evominiplus", "marvel1", "x2plus", "adb_box", "arivalink200", "atemio520", "atemio530", "atevio7500", "cuberevo", "cuberevo_2000hd", "cuberevo_250hd", "cuberevo_3000hd", "cuberevo_9500hd", "cuberevo_mini", "cuberevo_mini2", "fortis_hdbox", "hl101", "hs7110", "hs7119", "hs7420", "hs7429", "hs7810a", "hs7819", "ipbox55", "ipbox99", "ipbox9900", "octagon1008", "pace7241", "sagemcom88", "spark", "spark7162", "tf7700", "ufc960", "ufs910", "ufs912", "ufs913", "ufs922", "vip1_v2", "vip2_v1", "vitamin_hd5000", "gb800se", "gb800seplus", "gb800solo", "gb800ue", "gb800ueplus", "gbipbox", "gbquad", "gbquadplus", "gbtrio4k", "gbultrase", "gbultraue", "gbultraueh", "gbx1", "gbx2", "gbx3", "gbx3h", "su980", "atemio5x00", "atemio6000", "atemio6100", "atemio6200", "atemionemesis", "beyonwizt2", "beyonwizt3", "beyonwizt4", "bwidowx", "bwidowx2", "evoslim", "mbhybrid", "mbmini", "mbminiplus", "mbtwin", "mbultra", "opticumtt", "sezam1000hd", "sezam5000hd", "sezammarvel", "ventonhdx", "xpeedlx", "xpeedlx3", "ixussone", "ixusszero", "alien5", "c300", "c300pro", "c400plus", "k1plus", "k1pro", "k2pro", "k2prov2", "k3pro", "kvim2", "multibox", "v8plus", "x8hp", "sf4008", "sf5008", "sf8008", "axase3", "axase3c", "axodin", "axodinc", "classm", "evo", "evoe3hd", "galaxym6", "genius", "geniuse3hd", "maram9", "starsatlx", "odroidc2", "9900lx", "raspberrypi", "raspberrypi0", "raspberrypi2", "raspberrypi3", "tiviaraplus", "tiviarmin", "enibox", "evoslimse", "evoslimt2c", "mago", "sf108", "sf98", "t2cable", "tyrant", "x1plus", "xcombo", "ustym4kpro", "wetekhub", "wetekplay", "wetekplay2", "bcm7358", "spycat4k", "spycat4kcombo", "spycat4kmini", "vp7358ci", "beyonwizu4", "et13000", "vipercombo", "vipercombohdd", "viperslim", "vipert2c", "osmega", "osmini", "osminiplus", "osmio4k", "osnino", "osninoplus", "osninopro", "formuler1", "formuler3", "formuler4", "formuler4turbo", "bre2ze4k", "hd11", "hd1100", "hd1200", "hd1265", "hd1500", "hd2400", "hd500c", "hd51", "hd530c", "hd60", "hd61", "vs1000", "vs1500", "et1x000", "et7000mini", "gbquad4k", "gbue4k", "mbmicro", "mbmicrov2", "mbtwinplus", "lunix", "lunix3-4k", "lunix4k", "alphatriplehd", "spycat", "spycatmini", "spycatminiplus", "vuduo", "vuduo2", "vuduo4k", "vusolo", "vusolo2", "vusolo4k", "vusolose", "vuultimo", "vuultimo4k", "vuuno", "vuuno4k", "vuuno4kse", "vuzero", "vuzero4k", "xp1000", "xpeedc", "fusionhd", "fusionhdse", "galaxy4k", "purehd", "purehdse", "revo4k", "et10000", "et4x00", "et5x00", "et6x00", "et7x00", "et8000", "et8500", "et9x00", "h3", "h4", "h5", "h6", "h7", "h9", "h9combo", "i55", "i55plus", "lc", "sh1" };

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
