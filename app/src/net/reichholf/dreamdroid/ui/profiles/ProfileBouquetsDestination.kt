package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/** One profile's bouquet visibility as a phone NavHost destination. */
@Composable
fun ProfileBouquetsDestination(
    profileId: Int,
    modifier: Modifier = Modifier,
    viewModel: ProfileBouquetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    LaunchedEffect(profileId, viewModel) {
        viewModel.load(profileId)
    }
    ProfileBouquetsScreen(
        state = uiState,
        onToggle = viewModel::setVisible,
        onShowAll = viewModel::showAll,
        onReload = viewModel::reload,
        modifier = modifier
    )
}
