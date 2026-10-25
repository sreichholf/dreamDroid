package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

/**
 * The TV card row: a `dreamDroidTvCardColors` surface with a title, an optional summary
 * and an optional trailing control, 80 dp tall, focused at 1.03. The TV form's switch,
 * pick and dropdown rows are the same shape, so they line up.
 * [restContainer] replaces the resting fill, for a row that is the screen's primary action.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvListRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    restContainer: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        colors = dreamDroidTvCardColors(restContainer),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        shape = ClickableSurfaceDefaults.shape()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (!summary.isNullOrEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}

/** A toggle row: the whole row is the switch, so D-pad OK toggles it like Settings does. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    TvListRow(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        },
        onClick = { onCheckedChange(!checked) }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}
