package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class ProfilesListState(initial: List<ProfileListItem> = emptyList()) {
    val items: SnapshotStateList<ProfileListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<ProfileListItem>) {
        items.clear()
        items.addAll(next)
    }
}

fun ComposeView.bindProfilesScreen(
    state: ProfilesListState,
    addLabel: String,
    onProfileClick: (ProfileListItem) -> Unit,
    onProfileLongClick: (ProfileListItem) -> Unit,
    onAddClick: () -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            ProfilesScreen(
                profiles = state.items,
                addLabel = addLabel,
                onProfileClick = onProfileClick,
                onProfileLongClick = onProfileLongClick,
                onAddClick = onAddClick,
            )
        }
    }
}
