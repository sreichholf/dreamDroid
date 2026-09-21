package net.reichholf.dreamdroid.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import net.reichholf.dreamdroid.R

const val CHANGELOG_SHEET_TAG = "changelog_sheet"

/**
 * Material 3 [ModalBottomSheet] for the changelog (replaces the cramped [AlertDialog]).
 *
 * When hosted as a Navigation `dialog`, [clearNavigationDialogChrome] removes the empty
 * dialog window's dim so only the sheet scrim is visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogModalSheet(
    onDismiss: () -> Unit,
    markdown: String = rememberChangelogMarkdown(),
    clearNavigationDialogChrome: Boolean = false
) {
    if (clearNavigationDialogChrome) {
        ClearNavigationDialogChrome()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ChangelogSheetContent(
            markdown = markdown,
            onDismiss = onDismiss,
            modifier = Modifier.testTag(CHANGELOG_SHEET_TAG)
        )
    }
}

@Composable
internal fun ChangelogSheetContent(
    markdown: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bodyMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.changelog),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        ChangelogScreen(
            markdown = markdown,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun ClearNavigationDialogChrome() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setDimAmount(0f)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
