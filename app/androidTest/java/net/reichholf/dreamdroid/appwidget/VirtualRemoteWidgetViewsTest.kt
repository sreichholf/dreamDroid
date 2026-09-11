package net.reichholf.dreamdroid.appwidget

import android.preference.PreferenceManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualRemoteWidgetViewsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun prefs() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false)
            .putBoolean(VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + "42isFull", true)
            .commit()
    }

    @Test
    fun providerIsGlanceReceiver() {
        val provider = VirtualRemoteWidgetProvider()
        assertTrue(GlanceAppWidgetReceiver::class.java.isAssignableFrom(provider.javaClass))
        assertTrue(provider.glanceAppWidget is VirtualRemoteWidget)
    }

    @Test
    fun buildFullLayoutSetsProfileName() {
        val profile = Profile().apply { name = "Living Room" }
        val remoteViews = VirtualRemoteWidgetViews.build(context, 42, profile)
        val host = FrameLayout(context)
        val rooted = remoteViews.apply(context, host)
        val nameView = rooted.findViewById<TextView>(R.id.profile_name)
        assertEquals("Living Room", nameView.text.toString())
        assertEquals(View.VISIBLE, rooted.findViewById<View>(R.id.ButtonPlay).visibility)
        assertEquals(View.INVISIBLE, rooted.findViewById<View>(R.id.ButtonPlayPause).visibility)
    }
}
