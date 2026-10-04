package net.reichholf.dreamdroid.activities

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.isTelevision
import net.reichholf.dreamdroid.tv.activities.MainActivity as TvMainActivity
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone launcher entry opens the TV hub on a television and the phone shell elsewhere.
 * Each case runs on its own device type: a TV emulator for the hand-off, a phone for the shell.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTelevisionTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profiles: ProfileRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val isTelevision = instrumentation.targetContext.isTelevision()

    @Before
    fun setUp() {
        hiltRule.inject()
        // DreamDroid.onCreate does not run under HiltTestApplication; its load ends here.
        profiles.markLoaded()
    }

    @Test
    fun televisionHandsOffToTvHub() {
        assumeTrue(isTelevision)
        val tvHub = instrumentation.addMonitor(TvMainActivity::class.java.name, null, true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertEquals(1, tvHub.hits)
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        } finally {
            instrumentation.removeMonitor(tvHub)
        }
    }

    @Test
    fun phoneKeepsPhoneShell() {
        assumeFalse(isTelevision)
        val tvHub = instrumentation.addMonitor(TvMainActivity::class.java.name, null, true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertEquals(0, tvHub.hits)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            instrumentation.removeMonitor(tvHub)
        }
    }
}
