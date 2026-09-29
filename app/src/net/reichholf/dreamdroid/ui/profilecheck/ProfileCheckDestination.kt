package net.reichholf.dreamdroid.ui.profilecheck

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.text.UiText

/** The profile-check gate. The shell ViewModel runs the check and owns what the gate shows. */
@Composable
fun ProfileCheckDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    shell: ShellViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by shell.uiState.collectAsStateWithLifecycle()
    ShellTitle(UiText.Resource(R.string.app_name))
    ProfileCheckScreen(
        ui = uiState.profileCheck,
        onRecheck = shell::recheck,
        onProfiles = {
            shell.onProfilesFromGate()
            // Keep the gate under Profiles so Back returns to the check.
            handle.navigateAboveProfileCheck(PhoneNavRoutes.PROFILES)
        },
        modifier = modifier
    )
}
