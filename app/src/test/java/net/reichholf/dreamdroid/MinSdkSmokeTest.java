package net.reichholf.dreamdroid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MinSdkSmokeTest {
    @Test
    public void minSdkIs26() {
        assertEquals(26, BuildConfig.MIN_SDK);
    }
}
