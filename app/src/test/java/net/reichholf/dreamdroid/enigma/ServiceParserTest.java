package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServiceParserTest {
    @Test
    public void parsesGetServicesFixtureIntoServiceValues() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/getservices.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        long started = System.nanoTime();
        List<Service> services = ServiceParser.INSTANCE.parse(xml);
        long elapsedNanos = System.nanoTime() - started;
        System.out.println("ServiceParser.parse nanos=" + elapsedNanos);

        assertEquals(3, services.size());
        assertEquals(
                "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
                services.get(0).getReference()
        );
        assertEquals("Favourites (TV)", services.get(0).getName());
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", services.get(1).getReference());
        assertEquals("Das Erste HD", services.get(1).getName());
        assertEquals("1:64:1:0:0:0:0:0:0:0:", services.get(2).getReference());
        assertEquals("--------", services.get(2).getName());
    }
}
