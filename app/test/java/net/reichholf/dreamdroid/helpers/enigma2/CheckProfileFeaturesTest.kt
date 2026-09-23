package net.reichholf.dreamdroid.helpers.enigma2

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckProfileFeaturesTest {
    @Test
    fun version165EnablesSleepTimer() {
        assertTrue(CheckProfile.webInterfaceFeatures("1.6.5").sleepTimer)
    }

    @Test
    fun version164DisablesSleepTimer() {
        assertFalse(CheckProfile.webInterfaceFeatures("1.6.4").sleepTimer)
    }

    @Test
    fun emptyVersionDisablesSleepTimer() {
        assertFalse(CheckProfile.webInterfaceFeatures("0").sleepTimer)
    }
}
