package net.reichholf.dreamdroid.appwidget

import android.app.PendingIntent
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Field
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

    @Test
    fun twoAppWidgetIdsDoNotShareClickExtras() {
        val profile = Profile().apply { name = "Box" }
        val viewsA = VirtualRemoteWidgetViews.build(context, 101, profile)
        val viewsB = VirtualRemoteWidgetViews.build(context, 202, profile)
        assertEquals(101, widgetIdExtra(viewsA, R.id.ButtonPlay))
        assertEquals(202, widgetIdExtra(viewsB, R.id.ButtonPlay))
    }

    @Test
    fun missingProfileIsNullNotNpe() {
        val widgetId = 77
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(VirtualRemoteWidgetConfiguration.getProfileIdKey(widgetId), 9_001_337)
            .commit()
        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(context, widgetId)
        assertEquals(null, profile)
    }

    private fun widgetIdExtra(remoteViews: RemoteViews, viewId: Int): Int {
        val pendingIntent = clickPendingIntent(remoteViews, viewId)
        val intent = pendingIntentIntent(pendingIntent)
        return intent.getIntExtra(WidgetRemoteRequest.KEY_WIDGETID, Int.MIN_VALUE)
    }

    private fun clickPendingIntent(remoteViews: RemoteViews, viewId: Int): PendingIntent {
        val actionsField = declaredField(RemoteViews::class.java, "mActions")
        actionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val actions = actionsField.get(remoteViews) as ArrayList<Any>
        for (action in actions) {
            val idField = declaredField(action.javaClass, "viewId")
            idField.isAccessible = true
            if (idField.getInt(action) != viewId) continue
            val pendingField = pendingIntentField(action.javaClass) ?: continue
            pendingField.isAccessible = true
            val pending = pendingField.get(action) as PendingIntent?
            if (pending != null) return pending
        }
        throw AssertionError("no PendingIntent for view $viewId")
    }

    private fun pendingIntentIntent(pendingIntent: PendingIntent): Intent {
        val method = PendingIntent::class.java.getMethod("getIntent")
        return method.invoke(pendingIntent) as Intent
    }

    private fun declaredField(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun pendingIntentField(type: Class<*>): Field? {
        var current: Class<*>? = type
        while (current != null) {
            val match = current.declaredFields.firstOrNull { field ->
                PendingIntent::class.java.isAssignableFrom(field.type)
            }
            if (match != null) return match
            current = current.superclass
        }
        return null
    }
}
