package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceParserTest {
    @Test
    fun parsesGetServicesFixtureIntoServiceValues() {
        val xml = loadWebFixture("getservices.xml")
        val started = System.nanoTime()
        val services = ServiceParser.parse(xml)
        println("ServiceParser.parse nanos=${System.nanoTime() - started}")

        assertEquals(3, services.size)
        assertEquals(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            services[0].reference,
        )
        assertEquals("Favourites (TV)", services[0].name)
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", services[1].reference)
        assertEquals("Das Erste HD", services[1].name)
        assertEquals("1:64:1:0:0:0:0:0:0:0:", services[2].reference)
        assertEquals("--------", services[2].name)
    }

    @Test
    fun emptyXmlYieldsNoServices() {
        assertEquals(0, ServiceParser.parse("").size)
    }

    @Test
    fun malformedXmlYieldsNoServices() {
        assertEquals(0, ServiceParser.parse("<e2servicelist><e2service>").size)
    }
}
