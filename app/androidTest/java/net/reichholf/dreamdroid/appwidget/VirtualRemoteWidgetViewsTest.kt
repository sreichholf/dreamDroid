package net.reichholf.dreamdroid.appwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.ui.remote.VirtualRemoteButtons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VirtualRemoteWidgetViewsTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profiles: ProfileRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun prefs() {
        hiltRule.inject()
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

    @Test
    fun keysFitTheSmallestWidgetAndGrowWithIt() {
        val info = AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider == ComponentName(context, VirtualRemoteWidgetProvider::class.java)
        }
        val large = (480 * context.resources.displayMetrics.density).toInt()
        // 42 is configured full, 43 QuickZap.
        for (appWidgetId in listOf(42, 43)) {
            val small = laidOut(appWidgetId, info.minResizeWidth, info.minResizeHeight)
            val big = laidOut(appWidgetId, large, large)
            val keys = VirtualRemoteButtons.getRemoteButtons(false).map { it[0] }
                .filter { small.findViewById<View>(it) != null }
            assertTrue(keys.size > 10)
            for (key in keys) {
                val bounds = boundsIn(small, key)
                assertTrue(
                    "key $key outside ${small.width}x${small.height}: $bounds",
                    small.contains(bounds)
                )
                assertFalse("key $key is empty", bounds.isEmpty)
                assertTrue(boundsIn(big, key).height() > bounds.height())
            }
        }
    }

    @Test
    fun twoAppWidgetIdsDoNotShareClickExtras() {
        val intentA = VirtualRemoteWidgetViews.rcuButtonIntent(context, 101, "play")
        val intentB = VirtualRemoteWidgetViews.rcuButtonIntent(context, 202, "play")
        assertEquals(101, intentA.getIntExtra(WidgetRemoteRequest.KEY_WIDGETID, Int.MIN_VALUE))
        assertEquals(202, intentB.getIntExtra(WidgetRemoteRequest.KEY_WIDGETID, Int.MIN_VALUE))
        assertFalse(intentA.filterEquals(intentB))
    }

    @Test
    fun missingProfileIsNullNotNpe() = runBlocking<Unit> {
        val widgetId = 77
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(VirtualRemoteWidgetConfiguration.getProfileIdKey(widgetId), 9_001_337)
            .commit()
        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(
            context,
            profiles,
            widgetId
        )
        assertEquals(null, profile)
    }

    private fun laidOut(appWidgetId: Int, width: Int, height: Int): View {
        val profile = Profile().apply { name = "Living Room" }
        val root = VirtualRemoteWidgetViews.build(context, appWidgetId, profile)
            .apply(context, FrameLayout(context))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
        return root
    }

    private fun boundsIn(root: View, id: Int): Rect {
        val view = root.findViewById<View>(id)
        val rect = Rect(0, 0, view.width, view.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(view, rect)
        return rect
    }

    private fun View.contains(rect: Rect): Boolean = Rect(0, 0, width, height).contains(rect)
}
