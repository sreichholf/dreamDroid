package net.reichholf.dreamdroid.helpers.enigma2;

import org.junit.Assert;
import org.junit.Test;

public class ServiceDirectoryTest {
    @Test
    public void directoryFlagBitIsDirectory() {
        Assert.assertTrue(Service.isDirectory("1:1:1:0:0:0:0:0:0:0:"));
    }

    @Test
    public void providersPathWithoutFlagIsDirectory() {
        Assert.assertTrue(
                Service.isDirectory(
                        "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM PROVIDERS ORDER BY name"));
    }

    @Test
    public void bouquetPathWithoutFlagIsDirectory() {
        Assert.assertTrue(
                Service.isDirectory(
                        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"));
    }

    @Test
    public void plainServiceIsNotDirectory() {
        Assert.assertFalse(Service.isDirectory("1:0:1:6DCA:44C:1:C00000:0:0:0:"));
    }

    @Test
    public void nullOrEmptyIsNotDirectory() {
        Assert.assertFalse(Service.isDirectory(null));
        Assert.assertFalse(Service.isDirectory(""));
    }
}
