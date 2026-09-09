package net.reichholf.dreamdroid.ui.movies

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Movie as HashMovie
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MovieDetailDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun contentColorIsOnSurfaceInsideNightBottomSheet() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        val content = MovieDetailContent(
            title = "Tagesschau",
            serviceName = "Das Erste HD",
            description = "Evening news",
            descriptionExtended = "Die Nachrichten um 20 Uhr.",
            tags = listOf("News", "HD"),
            length = "00:15",
            date = "2026-09-09 20:00",
            fileSize = "123 MB",
        )
        val activity = composeRule.activity
        composeRule.runOnUiThread {
            val themed = ContextThemeWrapper(activity, R.style.Theme_DreamDroid_Night)
            val composeView = ComposeView(themed).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    DreamDroidTheme {
                        localContent = LocalContentColor.current
                        onSurface = MaterialTheme.colorScheme.onSurface
                        MovieDetailScreen(content = content)
                    }
                }
            }
            BottomSheetDialog(themed).apply {
                setContentView(composeView)
                show()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Evening news").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithText("HD").assertIsDisplayed()
        composeRule.onNodeWithText("123 MB").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }

    @Test
    fun typedAndHashMappersMatch() {
        val map = ExtendedHashMap()
        map.put(HashMovie.KEY_TITLE, "Film")
        map.put(HashMovie.KEY_SERVICE_NAME, "ARD")
        map.put(HashMovie.KEY_DESCRIPTION, "Short")
        map.put(HashMovie.KEY_DESCRIPTION_EXTENDED, "Long\\nLine")
        map.put(HashMovie.KEY_TAGS, "A B")
        map.put(HashMovie.KEY_LENGTH, "01:00")
        map.put(HashMovie.KEY_TIME_READABLE, "today")
        map.put(HashMovie.KEY_FILE_SIZE_READABLE, "1 GB")
        val hash = HashMovie(map)
        val typed = Movie(
            title = "Film",
            serviceName = "ARD",
            description = "Short",
            descriptionExtended = "Long\\nLine",
            tags = "A B",
            length = "01:00",
            timeReadable = "today",
            fileSizeReadable = "1 GB",
        )
        val fromHash = hash.toMovieDetailContent()
        val fromTyped = typed.toMovieDetailContent()
        assertEquals(fromHash, fromTyped)
        assertEquals(listOf("A", "B"), fromTyped.tags)
        assertEquals("Long\nLine", fromTyped.descriptionExtended)
    }
}
