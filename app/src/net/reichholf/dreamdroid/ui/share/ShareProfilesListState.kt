package net.reichholf.dreamdroid.ui.share

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class ShareProfilesListState(initial: List<ProfileListItem> = emptyList()) {
    val profiles: SnapshotStateList<ProfileListItem> = initial.toMutableStateList()
    var progress by mutableStateOf<IndeterminateProgressState?>(null)

    fun replaceAll(next: List<ProfileListItem>) {
        profiles.clear()
        profiles.addAll(next)
    }
}

fun ComposeView.bindShareProfilesScreen(
    state: ShareProfilesListState,
    onProfileClick: (ProfileListItem) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            ShareProfilesScreen(
                profiles = state.profiles,
                onProfileClick = onProfileClick,
            )
            IndeterminateProgressHost(state.progress)
        }
    }
}
