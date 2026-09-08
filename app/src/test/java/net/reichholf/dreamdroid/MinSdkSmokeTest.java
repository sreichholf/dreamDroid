package net.reichholf.dreamdroid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MinSdkSmokeTest {
    @Test
    public void minSdkIsAtLeast26() {
        assertEquals(26, BuildConfig.MIN_SDK);
        assertTrue(BuildConfig.MIN_SDK >= 26);
    }
}
