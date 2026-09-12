package net.reichholf.dreamdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinSdkSmokeTest {
    @Test
    fun minSdkIs26() {
        assertEquals(26, BuildConfig.MIN_SDK)
    }
}
