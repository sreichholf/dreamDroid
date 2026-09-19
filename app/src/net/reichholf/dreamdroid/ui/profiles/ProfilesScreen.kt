package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

const val PROFILE_ROW_EDIT_TAG_PREFIX = "profile_row_edit_"

@Composable
fun ProfilesScreen(
    profiles: List<ProfileListItem>,
    onProfileClick: (ProfileListItem) -> Unit,
    onProfileEdit: (ProfileListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                onClick = { onProfileClick(profile) },
                onEdit = { onProfileEdit(profile) }
            )
        }
    }
}

@Composable
private fun ProfileRow(profile: ProfileListItem, onClick: () -> Unit, onEdit: () -> Unit) {
    val tileColor =
        if (profile.active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ListRowSurface(color = tileColor) {
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
            trailingContent = {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag(PROFILE_ROW_EDIT_TAG_PREFIX + profile.id)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_edit),
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = listRowItemColors(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        )
    }
}
