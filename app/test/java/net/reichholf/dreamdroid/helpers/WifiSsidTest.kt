package net.reichholf.dreamdroid.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WifiSsidTest {
    @Test
    fun unquoteWifiSsid_stripsWrappingQuotes() {
        assertEquals("HomeNet", WifiSsid.unquoteWifiSsid("\"HomeNet\""))
    }

    @Test
    fun unquoteWifiSsid_keepsUnquoted() {
        assertEquals("HomeNet", WifiSsid.unquoteWifiSsid("HomeNet"))
    }

    @Test
    fun unquoteWifiSsid_unknownIsNull() {
        assertNull(WifiSsid.unquoteWifiSsid("<unknown ssid>"))
    }

    @Test
    fun unquoteWifiSsid_quotedUnknownIsNull() {
        assertNull(WifiSsid.unquoteWifiSsid("\"<unknown ssid>\""))
    }

    @Test
    fun unquoteWifiSsid_nullIsNull() {
        assertNull(WifiSsid.unquoteWifiSsid(null))
    }

    @Test
    fun unquoteWifiSsid_emptyIsNull() {
        assertNull(WifiSsid.unquoteWifiSsid(""))
    }

    @Test
    fun unquoteWifiSsid_singleQuoteCharUnchanged() {
        assertEquals("\"", WifiSsid.unquoteWifiSsid("\""))
    }
}
