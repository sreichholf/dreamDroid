package net.reichholf.dreamdroid.helpers.enigma2

import android.util.Log
import net.reichholf.dreamdroid.Profile
import java.io.IOException
import java.net.InetAddress
import java.util.Arrays
import java.util.Locale
import javax.jmdns.JmDNS

object DeviceDetector {
    @JvmField
    var LOG_TAG: String = DeviceDetector::class.java.name

    @JvmField
    val KNOWN_HOSTNAMES: Array<String> = arrayOf(
        "dm500hd", "dm800", "dm800se", "dm7020hd", "dm7025", "dm8000", "dm800sev2",
        "dm500hdsev2", "dm7020hdv2", "dm7080", "dm820", "dm520", "dm525", "dm900",
    )

    @JvmStatic
    fun getAvailableHosts(): ArrayList<Profile> {
        val profiles = ArrayList<Profile>()
        for (hostname in KNOWN_HOSTNAMES) {
            try {
                val host = InetAddress.getByName(hostname)
                if (!host.isReachable(1500)) continue
                val simpleRemote = false
                val ip = host.hostAddress ?: continue
                val p = Profile.getDefault()
                p.setName(hostname)
                p.setHost(ip)
                p.setStreamHost(ip)
                p.setPort(80)
                p.setUser("root")
                p.setSimpleRemote(simpleRemote)
                addToList(profiles, p)
            } catch (e: IOException) {
                Log.w(LOG_TAG, e.message ?: e.toString())
            }
        }

        try {
            val jmdns = JmDNS.create()
            val si = jmdns.list("_http._tcp.local.")
            for (s in si) {
                Log.i(LOG_TAG, Arrays.toString(s.hostAddresses))
                if (s.name.lowercase(Locale.US).matches(Regex("dm[0-9]{1,4}.*"))) {
                    val address = s.hostAddresses[0] ?: continue
                    val port = s.port
                    val simpleRemote = false
                    val p = Profile.getDefault()
                    p.setName(s.name)
                    p.setHost(address)
                    p.setStreamHost(address)
                    p.setPort(port)
                    p.setUser("root")
                    p.setSimpleRemote(simpleRemote)
                    addToList(profiles, p)
                }
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, e.message ?: e.toString())
        }

        return profiles
    }

    private fun addToList(list: ArrayList<Profile>, profile: Profile) {
        for (p in list) {
            if (profile.host == p.host) {
                list.remove(p)
                break
            }
        }
        list.add(profile)
    }
}
