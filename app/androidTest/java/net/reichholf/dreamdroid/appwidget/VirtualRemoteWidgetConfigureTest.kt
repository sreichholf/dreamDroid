package net.reichholf.dreamdroid.appwidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.widget.RemoteViews
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Binds a real widget to a test [AppWidgetHost] so its Glance session is running when the
 * configure step saves the style and profile, as on a launcher (#606).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VirtualRemoteWidgetConfigureTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profiles: ProfileRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var widgetHost: TestWidgetHost
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @Before
    fun bindWidget() {
        hiltRule.inject()
        shell("appwidget grantbind --package ${context.packageName} --user current")
        instrumentation.runOnMainSync {
            widgetHost = TestWidgetHost(context)
            widgetHost.startListening()
        }
        appWidgetId = widgetHost.allocateAppWidgetId()
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, VirtualRemoteWidgetProvider::class.java)
        assertTrue(manager.bindAppWidgetIdIfAllowed(appWidgetId, provider))
        instrumentation.runOnMainSync {
            widgetHost.createView(context, appWidgetId, manager.getAppWidgetInfo(appWidgetId))
        }
    }

    @After
    fun deleteWidget() {
        instrumentation.runOnMainSync {
            widgetHost.stopListening()
            widgetHost.deleteHost()
        }
        VirtualRemoteWidgetConfiguration.deleteWidgetConfiguration(context, appWidgetId)
    }

    @Test
    fun configuringARunningWidgetShowsTheChosenLayoutAndProfile() = runBlocking<Unit> {
        val box = Profile().apply {
            name = "Living Room"
            host = "box.local"
        }
        profiles.save(box)
        awaitContent(WidgetContent(context.getString(R.string.no_profile_available), full = false))

        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(VirtualRemoteWidgetConfiguration.getProfileIdKey(appWidgetId), box.id!!)
            .putBoolean(VirtualRemoteWidgetConfiguration.getIsFullKey(appWidgetId), true)
            .commit()
        VirtualRemoteWidgetProvider.updateWidget(context, appWidgetId, profiles.profile(box.id!!))

        awaitContent(WidgetContent("Living Room", full = true))
    }

    private suspend fun awaitContent(expected: WidgetContent) {
        withTimeout(30_000) { widgetHost.content.filterNotNull().first { it == expected } }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    private data class WidgetContent(val profileName: String, val full: Boolean)

    /** Reports what the widget shows after every update the host applies. */
    private class TestWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        val content = MutableStateFlow<WidgetContent?>(null)

        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = object : AppWidgetHostView(context) {
            override fun updateAppWidget(remoteViews: RemoteViews?) {
                super.updateAppWidget(remoteViews)
                val name = findViewById<TextView>(R.id.profile_name) ?: return
                content.value = WidgetContent(
                    profileName = name.text.toString(),
                    full = findViewById<TextView>(R.id.Button1) != null
                )
            }
        }
    }

    private companion object {
        const val HOST_ID = 606
    }
}
