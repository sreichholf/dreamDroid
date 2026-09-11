package net.reichholf.dreamdroid.ui.movies

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Movie as HashMovie
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2.1g-ii-d: movie detail hosts as Material 3 [MovieDetailModalSheet] in composition
 * (no View [com.google.android.material.bottomsheet.BottomSheetDialog]).
 */
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
    fun contentColorIsOnSurfaceInsideNightModalSheet() {
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
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                MovieDetailModalSheet(
                    content = content,
                    onDismiss = {},
                )
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
    fun tvFullscreenRendersWithoutHeightCap() {
        val content = MovieDetailContent(
            title = "Tagesschau",
            serviceName = "Das Erste HD",
            description = "Evening news",
            descriptionExtended = "Die Nachrichten um 20 Uhr.",
            tags = listOf("News"),
            length = "00:15",
            date = "2026-09-09 20:00",
            fileSize = "123 MB",
        )
        composeRule.setContent {
            DreamDroidTheme {
                MovieDetailScreen(content = content, heightCap = null)
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("123 MB").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
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
