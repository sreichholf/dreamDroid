package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

/** D-pad TV profile list: Add, then activate / edit / delete per Room profile. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfilesScreen(
    profiles: List<ProfileListItem>,
    onAdd: () -> Unit,
    onActivate: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDeleteConfirmed: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<ProfileListItem?>(null) }
    val activeFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val activeIndex = profiles.indexOfFirst { it.active }

    LaunchedEffect(activeIndex) {
        if (activeIndex < 0) {
            return@LaunchedEffect
        }
        listState.scrollToItem(activeIndex + 1)
        try {
            activeFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // Item not yet attached.
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_profiles_list")
    ) {
        item(key = "add") {
            Surface(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .testTag("tv_profiles_add"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.profile_add),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        items(profiles, key = { it.id }) { profile ->
            TvProfileRow(
                profile = profile,
                activeFocusRequester = if (profile.active) {
                    activeFocusRequester
                } else {
                    null
                },
                onActivate = { onActivate(profile.id) },
                onEdit = { onEdit(profile.id) },
                onDelete = {
                    onDelete(profile.id)
                    pendingDelete = profile
                }
            )
        }
    }

    pendingDelete?.let { profile ->
        ConfirmAlertDialog(
            title = profile.name,
            message = stringResource(R.string.confirm_delete_profile),
            onDismiss = { pendingDelete = null },
            onConfirm = { onDeleteConfirmed(profile.id) },
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvProfileRow(
    profile: ProfileListItem,
    activeFocusRequester: FocusRequester?,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onActivate,
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .then(
                    if (activeFocusRequester != null) {
                        Modifier.focusRequester(activeFocusRequester)
                    } else {
                        Modifier
                    }
                )
                .testTag("tv_profiles_row_${profile.id}")
                .semantics { selected = profile.active },
            colors = tvProfilesActivateCardColors(profile.active),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = profile.host,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Surface(
            onClick = onEdit,
            modifier = Modifier
                .width(72.dp)
                .height(72.dp)
                .testTag("tv_profiles_edit_${profile.id}"),
            colors = dreamDroidTvCardColors(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_edit),
                    contentDescription = stringResource(R.string.edit)
                )
            }
        }
        Surface(
            onClick = onDelete,
            modifier = Modifier
                .width(72.dp)
                .height(72.dp)
                .testTag("tv_profiles_delete_${profile.id}"),
            colors = dreamDroidTvCardColors(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_delete),
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

/**
 * Active (current) activate-card: [dreamDroidTvCardColors] with unfocused container
 * [PhoneMaterialTheme.colorScheme.secondaryContainer]. Focused stays inverse.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun tvProfilesActivateCardColors(active: Boolean): ClickableSurfaceColors {
    if (!active) {
        return dreamDroidTvCardColors()
    }
    val phone = PhoneMaterialTheme.colorScheme
    return ClickableSurfaceDefaults.colors(
        containerColor = phone.secondaryContainer,
        contentColor = phone.onSurface,
        focusedContainerColor = phone.inverseSurface,
        focusedContentColor = phone.inverseOnSurface,
        pressedContainerColor = phone.inverseSurface,
        pressedContentColor = phone.inverseOnSurface
    )
}
