package net.reichholf.dreamdroid.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.LocalNetworkPermission
import net.reichholf.dreamdroid.helpers.isTelevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone activity on the target SDK, driven through ActivityScenario, View and window
 * APIs rather than Compose: from API 37 Espresso's idling resource calls the removed
 * `InputManager.getInstance()`, so a Compose test rule dies in `Espresso.onIdle` before the
 * first assertion.
 *
 * The test graph has no profile, because `DreamDroid.onCreate` does not run under
 * `HiltTestApplication` and [setUp] only marks the profile load done, so [MainActivity]
 * opens the setup assistant. That shapes the back case: the enabled callback is the
 * wizard's Compose `BackHandler`, not the shell's leave callback, which only
 * `startPhoneShell` registers. Reaching the shell needs a current profile, and that path
 * prompts for `ACCESS_LOCAL_NETWORK` on API 37 as soon as the activity starts.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTargetSdkTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profiles: ProfileRepository

    private val isTelevision =
        InstrumentationRegistry.getInstrumentation().targetContext.isTelevision()

    @Before
    fun setUp() {
        assumeFalse("the phone screen is not the TV hub", isTelevision)
        hiltRule.inject()
        // DreamDroid.onCreate does not run under HiltTestApplication; its load ends here.
        profiles.markLoaded()
    }

    /** The real "does the app start on the target SDK" assertion. */
    @Test
    fun theActivityRunsOnThisPlatform() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse("the activity is finishing after launch", activity.isFinishing)
                assertFalse("the activity is destroyed after launch", activity.isDestroyed)
            }
        }
    }

    /**
     * Insets are dispatched to the window and the activity content fills it.
     *
     * The shell pads for the system bars inside Compose, so the View-level content spans
     * under them on purpose: asserting that the content view sits below a non-zero status
     * bar would fail by design. What is asserted here is what the Compose side consumes,
     * plus that the content is laid out inside the window. On a device that reports no
     * status or navigation bar the values are 0, so only non-negative is required.
     */
    @Test
    fun windowInsetsReachTheShell() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitWindowFrames(scenario)
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val windowInsets = decor.rootWindowInsets
                assertNotNull("decorView reports no window insets", windowInsets)

                val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets, decor)
                val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                assertTrue(
                    "negative status bar insets: $statusBars",
                    statusBars.top >= 0 && statusBars.bottom >= 0 &&
                        statusBars.left >= 0 && statusBars.right >= 0
                )
                assertTrue(
                    "negative navigation bar insets: $navigationBars",
                    navigationBars.top >= 0 && navigationBars.bottom >= 0 &&
                        navigationBars.left >= 0 && navigationBars.right >= 0
                )

                val content = activity.findViewById<View>(android.R.id.content)
                assertNotNull("the activity has no content view", content)
                assertTrue(
                    "the content view is not laid out: ${content.width}x${content.height}",
                    content.width > 0 && content.height > 0
                )
                assertTrue("the content view has no shell", content.childCount > 0)
                val shell = content.getChildAt(0)
                assertTrue(
                    "the shell view is not laid out: ${shell.width}x${shell.height}",
                    shell.width > 0 && shell.height > 0
                )

                val windowFrame = Rect()
                assertTrue("the decor view is not visible", decor.getGlobalVisibleRect(windowFrame))
                val contentFrame = Rect()
                assertTrue(
                    "the content view is not visible",
                    shell.getGlobalVisibleRect(contentFrame)
                )
                assertTrue("the shell draws outside the window", windowFrame.contains(contentFrame))
            }
        }
    }

    /**
     * Back reaches an enabled callback on the activity's dispatcher.
     *
     * With no current profile the wizard is at its Welcome step, where the wizard's
     * `BackHandler` calls `onLeave` and [MainActivity] finishes; a pop to an earlier wizard
     * step happens on every later step instead. Both follow from `SetupAssistantViewModel`
     * and are asserted against what is on screen: Welcome, so the activity ends.
     */
    @Test
    fun backIsHandledByTheShell() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitWindowFrames(scenario)
            scenario.onActivity { activity ->
                assertTrue(
                    "the activity has no enabled back callback",
                    activity.onBackPressedDispatcher.hasEnabledCallbacks()
                )
                activity.onBackPressedDispatcher.onBackPressed()
            }
            assertEquals(
                "Back on the setup welcome step leaves the wizard",
                Lifecycle.State.DESTROYED,
                scenario.state
            )
        }
    }

    /**
     * The LAN gate reads the platform check on API 37 and stays open below it. Runs inside
     * the launched activity, so the context is the real one the app uses.
     */
    @Test
    fun theLocalNetworkGateMatchesThePlatform() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val granted = LocalNetworkPermission.isGranted(activity)
                if (LocalNetworkPermission.isRequired()) {
                    val platformGranted =
                        ContextCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.ACCESS_LOCAL_NETWORK
                        ) == PackageManager.PERMISSION_GRANTED
                    assertEquals(platformGranted, granted)
                } else {
                    assertTrue("LAN access is implicit below API 37", granted)
                }
            }
        }
    }

    /**
     * Waits for two drawn frames of the activity window: the first traversal dispatches the
     * window insets, and the composition that registers back handlers is applied before it.
     */
    private fun awaitWindowFrames(scenario: ActivityScenario<MainActivity>) {
        val drawn = CountDownLatch(1)
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val observer = decor.viewTreeObserver
            observer.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    private var frames = 0

                    override fun onPreDraw(): Boolean {
                        frames++
                        if (frames < FRAMES_BEFORE_READ) {
                            decor.invalidate()
                        } else {
                            observer.removeOnPreDrawListener(this)
                            drawn.countDown()
                        }
                        return true
                    }
                }
            )
            decor.invalidate()
        }
        assertTrue(
            "the activity window drew no frame",
            drawn.await(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
    }

    private companion object {
        const val FRAMES_BEFORE_READ = 2

        const val FRAME_TIMEOUT_SECONDS = 10L
    }
}
