package net.reichholf.dreamdroid.ui.zap

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceParser
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZapListMapperTest {
    @Test
    fun rowsFromDropsMarkersAndKeepsChannels() {
        val parsed = ServiceParser.parse(loadWebFixture("getservices.xml"))
        assertEquals(3, parsed.size)

        val rows = ZapListMapper.rowsFrom(parsed)
        assertEquals(2, rows.size)
        assertEquals("Favourites (TV)", rows[0].name)
        assertEquals("Das Erste HD", rows[1].name)
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", rows[1].reference)
        for (row in rows) {
            assertTrue(!net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(row.reference))
        }
    }

    @Test
    fun rowsFromEmptyListIsEmpty() {
        assertEquals(0, ZapListMapper.rowsFrom(emptyList()).size)
    }

    @Test
    fun bouquetFromReadsReferenceAndName() {
        val map = ExtendedHashMap()
        map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE, "1:7:1:0:0:0:0:0:0:0:")
        map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME, "Favourites (TV)")
        val bouquet = ZapListMapper.bouquetFrom(map)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", bouquet.reference)
        assertEquals("Favourites (TV)", bouquet.name)
    }

    @Test
    fun bouquetFromNullMapIsEmptyService() {
        val bouquet = ZapListMapper.bouquetFrom(null)
        assertEquals("", bouquet.reference)
        assertEquals("", bouquet.name)
    }

    @Test
    fun toBouquetMapWritesReferenceAndName() {
        val service = Service("1:7:1:0:0:0:0:0:0:0:", "Favourites (TV)")
        val map = ZapListMapper.toBouquetMap(service)
        assertEquals(
            "1:7:1:0:0:0:0:0:0:0:",
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE),
        )
        assertEquals("Favourites (TV)", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME))
        assertEquals(service.reference, ZapListMapper.bouquetFrom(map).reference)
        assertEquals(service.name, ZapListMapper.bouquetFrom(map).name)
    }

    @Test
    fun toBouquetMapNullIsEmptyKeys() {
        val map = ZapListMapper.toBouquetMap(null)
        assertEquals("", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE))
        assertEquals("", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME))
    }
}
