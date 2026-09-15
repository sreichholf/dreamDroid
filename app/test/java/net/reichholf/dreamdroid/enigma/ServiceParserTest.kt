package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
            services[0].reference
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

    @Test
    fun stripsControlCharactersFromServiceName() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2servicelist>
            <e2service>
            <e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>
            <e2servicename>ZDF\u0001HD</e2servicename>
            </e2service>
            </e2servicelist>
        """.trimIndent().replace("\\u0001", "\u0001")
        val services = ServiceParser.parse(xml)
        assertEquals(1, services.size)
        assertEquals("ZDFHD", services[0].name)
    }
}
