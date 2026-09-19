package net.reichholf.dreamdroid.intents

import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.activities.VideoActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentFactoryTest {
    @After
    fun restoreIntegratedPlayer() {
        PreferenceManager.getDefaultSharedPreferences(context())
            .edit()
            .remove(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER)
            .commit()
    }

    @Test
    fun videoPlaybackUsesIntegratedPlayerByDefault() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit()
            .remove(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER)
            .commit()
        assertTrue(IntentFactory.usesIntegratedPlayer(context()))
        val intent = IntentFactory.videoPlaybackIntent(context(), STREAM_URI)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("video/*", intent.type)
        assertEquals(STREAM_URI, intent.dataString)
        assertEquals(VideoActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun videoPlaybackUsesExternalPlayerWhenSettingOff() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit()
            .putBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, false)
            .commit()
        assertFalse(IntentFactory.usesIntegratedPlayer(context()))
        val intent = IntentFactory.videoPlaybackIntent(context(), STREAM_URI)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("video/*", intent.type)
        assertEquals(STREAM_URI, intent.dataString)
        assertNull(intent.component)
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        private const val STREAM_URI = "http://box.local:8001/1%3A0%3A1"
    }
}
