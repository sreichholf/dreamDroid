package net.reichholf.dreamdroid.ui.nav

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Coordinator [R.id.fab_main] is shared across destinations. Successor binds must
 * win over a stale [DisposableEffect] dispose (NavHost / load remount flicker).
 */
class ShellFabHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun remountKeepsLabeledFabVisible() {
        val fab = hostDualpaneShell {
            var remount by remember { mutableIntStateOf(0) }
            key(remount) {
                BindShellFab(
                    contentDescription = "Add Profile",
                    iconRes = R.drawable.ic_action_fab_add,
                    onClick = {},
                    text = "Add Profile"
                )
            }
            Column {
                Button(onClick = { remount += 1 }) { Text("remount") }
            }
        }
        waitUntilVisible(fab)
        assertLabeled(fab, "Add Profile")

        composeRule.onNodeWithText("remount").assertIsDisplayed()
        composeRule.onNodeWithText("remount").performClick()
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        waitUntilVisible(fab)
        assertLabeled(fab, "Add Profile")
    }

    @Test
    fun successorBindKeepsFabVisibleAndUpdatesLabel() {
        val fab = hostDualpaneShell {
            var owner by remember { mutableIntStateOf(1) }
            val label = if (owner == 1) "Add Profile" else "New timer"
            if (owner != 0) {
                key(owner) {
                    BindShellFab(
                        contentDescription = label,
                        iconRes = R.drawable.ic_action_fab_add,
                        onClick = {},
                        text = label
                    )
                }
            }
            Column {
                Button(onClick = { owner = 2 }) { Text("swap") }
                Button(onClick = { owner = 0 }) { Text("clear") }
            }
        }
        waitUntilVisible(fab)
        assertLabeled(fab, "Add Profile")

        composeRule.onNodeWithText("swap").assertIsDisplayed()
        composeRule.onNodeWithText("swap").performClick()
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fab.visibility == View.VISIBLE &&
                fab.contentDescription == "New timer"
        }
        assertLabeled(fab, "New timer")

        composeRule.onNodeWithText("clear").performClick()
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fab.visibility != View.VISIBLE
        }
    }

    private fun hostDualpaneShell(content: @Composable () -> Unit): ExtendedFloatingActionButton {
        val activity = composeRule.activity
        lateinit var fab: ExtendedFloatingActionButton
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(R.layout.dualpane)
            fab = activity.findViewById(R.id.fab_main)
            val shell = activity.findViewById<ComposeView>(R.id.shell_destination_nav)
            shell.visibility = View.VISIBLE
            shell.layoutParams = shell.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            shell.setViewTreeLifecycleOwner(activity)
            shell.setViewTreeViewModelStoreOwner(activity)
            shell.setViewTreeSavedStateRegistryOwner(activity)
            shell.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            shell.setContent {
                DreamDroidTheme {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
        return fab
    }

    private fun waitUntilVisible(fab: ExtendedFloatingActionButton) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fab.visibility == View.VISIBLE
        }
    }

    private fun assertLabeled(fab: ExtendedFloatingActionButton, label: String) {
        assertEquals(label, fab.contentDescription)
        assertEquals(label, fab.text.toString())
        assertTrue(fab.isExtended)
        assertEquals(View.VISIBLE, fab.visibility)
    }
}
