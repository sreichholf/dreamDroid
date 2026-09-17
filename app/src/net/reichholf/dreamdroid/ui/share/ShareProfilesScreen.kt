package net.reichholf.dreamdroid.ui.share

import androidx.compose.foundation.clickable
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
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem

@Composable
fun ShareProfilesScreen(
    profiles: List<ProfileListItem>,
    onProfileClick: (ProfileListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(profiles, key = { it.id }) { profile ->
            ShareProfileRow(
                profile = profile,
                onClick = { onProfileClick(profile) }
            )
        }
    }
}

@Composable
private fun ShareProfileRow(profile: ProfileListItem, onClick: () -> Unit) {
    ListRowSurface(modifier = Modifier.clickable(onClick = onClick)) {
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
