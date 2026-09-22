package net.reichholf.dreamdroid.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareProfilesHost(
    title: String,
    state: ShareProfilesListState,
    onProfileClick: (ProfileListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(title) })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ShareProfilesScreen(
                profiles = state.profiles,
                onProfileClick = onProfileClick,
                clicksEnabled = state.progress == null
            )
            IndeterminateProgressHost(state.progress)
        }
    }
}

@Composable
fun ShareProfilesScreen(
    profiles: List<ProfileListItem>,
    onProfileClick: (ProfileListItem) -> Unit,
    modifier: Modifier = Modifier,
    clicksEnabled: Boolean = true
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(profiles, key = { it.id }) { profile ->
            ShareProfileRow(
                profile = profile,
                onClick = { onProfileClick(profile) },
                clicksEnabled = clicksEnabled
            )
        }
    }
}

@Composable
private fun ShareProfileRow(
    profile: ProfileListItem,
    onClick: () -> Unit,
    clicksEnabled: Boolean = true
) {
    ListRowSurface(
        modifier = Modifier.clickable(enabled = clicksEnabled, onClick = onClick)
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
