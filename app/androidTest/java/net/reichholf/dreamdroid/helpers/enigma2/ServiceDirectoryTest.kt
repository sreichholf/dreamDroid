package net.reichholf.dreamdroid.helpers.enigma2

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceDirectoryTest {
    @Test
    fun directoryFlagBitIsDirectory() {
        assertTrue(Service.isDirectory("1:1:1:0:0:0:0:0:0:0:"))
    }

    @Test
    fun providersPathWithoutFlagIsDirectory() {
        assertTrue(
            Service.isDirectory(
                "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM PROVIDERS ORDER BY name",
            ),
        )
    }

    @Test
    fun bouquetPathWithoutFlagIsDirectory() {
        assertTrue(
            Service.isDirectory(
                "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            ),
        )
    }

    @Test
    fun plainServiceIsNotDirectory() {
        assertFalse(Service.isDirectory("1:0:1:6DCA:44C:1:C00000:0:0:0:"))
    }

    @Test
    fun nullOrEmptyIsNotDirectory() {
        assertFalse(Service.isDirectory(null))
        assertFalse(Service.isDirectory(""))
    }
}
