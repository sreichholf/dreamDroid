package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

@Composable
fun ProfilesScreen(
    profiles: List<ProfileListItem>,
    onProfileClick: (ProfileListItem) -> Unit,
    onProfileLongClick: (ProfileListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                onClick = { onProfileClick(profile) },
                onLongClick = { onProfileLongClick(profile) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileRow(profile: ProfileListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tileColor =
        if (profile.active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ListRowSurface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = tileColor
    ) {
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
