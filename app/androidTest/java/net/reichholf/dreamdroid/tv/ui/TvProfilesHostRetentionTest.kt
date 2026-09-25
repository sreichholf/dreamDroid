package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class TvProfilesHostRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun leavingCompositionKeepsAddDraft() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TvProfilesHostViewModel(app)
        viewModel.showAdd()
        val draft = checkNotNull(viewModel.editState)
        var shown by mutableStateOf(true)
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    if (shown) {
                        TvProfilesHost(viewModel = viewModel)
                    }
                }
            }
        }
        composeRule.onNodeWithTag("tv_profiles_list").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(app.getString(R.string.profile_name))
            .assertIsDisplayed()

        composeRule.runOnIdle {
            draft.name = "Draft Box"
        }
        composeRule.onNodeWithText("Draft Box").assertIsDisplayed()

        shown = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Draft Box").assertDoesNotExist()

        shown = true
        composeRule.waitForIdle()
        assertSame(draft, viewModel.editState)
        composeRule.onNodeWithContentDescription(app.getString(R.string.profile_name))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Draft Box").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_profiles_list").assertDoesNotExist()
    }
}
