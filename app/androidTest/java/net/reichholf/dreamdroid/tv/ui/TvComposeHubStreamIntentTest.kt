package net.reichholf.dreamdroid.tv.ui

import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvComposeHubStreamIntentTest {
    @After
    fun restoreIntegratedPlayer() {
        PreferenceManager.getDefaultSharedPreferences(context())
            .edit()
            .remove(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER)
            .commit()
    }

    @Test
    fun serviceAndMovieStreamsUseIntegratedPlayerByDefault() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit()
            .remove(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER)
            .commit()
        val serviceIntent = TvComposeHubHost.streamServiceIntent(
            context(),
            sampleService(),
            "1:7:1:0:0:0:0:0:0:0:",
            sampleStream()
        )
        assertEquals(VideoActivity::class.java.name, serviceIntent.component?.className)
        assertEquals("Now Show", serviceIntent.getStringExtra("title"))
        assertEquals(
            sampleService().serviceReference,
            serviceIntent.getStringExtra("serviceRef")
        )
        assertEquals(SERVICE_URL, serviceIntent.dataString)
        assertTrue(serviceIntent.hasExtra("serviceInfo"))

        val movieIntent = TvComposeHubHost.streamMovieIntent(context(), sampleMovie(), MOVIE_URL)
        assertEquals(VideoActivity::class.java.name, movieIntent.component?.className)
        assertEquals("Demo Recording", movieIntent.getStringExtra("title"))
        assertEquals(MOVIE_URL, movieIntent.dataString)
        assertTrue(movieIntent.hasExtra("serviceInfo"))
    }

    @Test
    fun serviceAndMovieStreamsUseExternalPlayerWhenSettingOff() {
        PreferenceManager.getDefaultSharedPreferences(context()).edit()
            .putBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, false)
            .commit()
        val serviceIntent = TvComposeHubHost.streamServiceIntent(
            context(),
            sampleService(),
            "1:7:1:0:0:0:0:0:0:0:",
            sampleStream()
        )
        assertEquals(Intent.ACTION_VIEW, serviceIntent.action)
        assertEquals("video/*", serviceIntent.type)
        assertNull(serviceIntent.component)
        assertFalse(serviceIntent.hasExtra("serviceInfo"))

        val movieIntent = TvComposeHubHost.streamMovieIntent(context(), sampleMovie(), MOVIE_URL)
        assertEquals(Intent.ACTION_VIEW, movieIntent.action)
        assertNull(movieIntent.component)
        assertFalse(movieIntent.hasExtra("serviceInfo"))
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleService() = ServiceNowNext(
        serviceReference = "1:0:1:1:1:1:1:0:0:0:",
        serviceName = "Demo Channel",
        now = Event(title = "Now Show")
    )

    private fun sampleStream() = LiveStream.Ready(sampleService().serviceReference, SERVICE_URL)

    private fun sampleMovie() = Movie(
        reference = "1:0:0:0:0:0:0:0:0:0:",
        title = "Demo Recording",
        fileName = "demo.ts"
    )

    private companion object {
        const val SERVICE_URL = "http://127.0.0.1:8001/1:0:1:1:1:1:1:0:0:0:"
        const val MOVIE_URL = "http://127.0.0.1:80/file?file=demo.ts"
    }
}
