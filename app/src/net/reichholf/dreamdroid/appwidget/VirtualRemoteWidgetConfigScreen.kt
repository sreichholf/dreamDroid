package net.reichholf.dreamdroid.appwidget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem

@Composable
fun VirtualRemoteWidgetConfigScreen(
    profiles: List<ProfileListItem>,
    isFull: Boolean,
    onStyleFullChange: (Boolean) -> Unit,
    onProfileClick: (ProfileListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.remote_widget_config_title_style),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = ListRowHorizontalInset + 16.dp,
                end = ListRowHorizontalInset + 16.dp,
                top = 8.dp,
                bottom = 8.dp
            )
        )
        Column(modifier = Modifier.selectableGroup()) {
            StyleOptionRow(
                label = stringResource(R.string.quickzap),
                selected = !isFull,
                onClick = { onStyleFullChange(false) }
            )
            StyleOptionRow(
                label = stringResource(R.string.standard),
                selected = isFull,
                onClick = { onStyleFullChange(true) }
            )
        }
        Text(
            text = stringResource(R.string.remote_widget_config_title_profile),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = ListRowHorizontalInset + 16.dp,
                end = ListRowHorizontalInset + 16.dp,
                top = 8.dp,
                bottom = 8.dp
            )
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(profiles, key = { it.id }) { profile ->
                ListRowSurface(modifier = Modifier.clickable { onProfileClick(profile) }) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                text = profile.host,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = listRowItemColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListRowSurface(
        modifier = Modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.RadioButton
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
