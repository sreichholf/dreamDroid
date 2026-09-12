package net.reichholf.dreamdroid.ui.profilecheck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment

@Composable
fun ProfileCheckDestination(
	hostFragment: PhoneNavHostFragment,
	modifier: Modifier = Modifier,
) {
	val ui by hostFragment.profileCheckUiFlow().collectAsState()
	val activity = LocalContext.current as? MainActivity
	ProfileCheckScreen(
		ui = ui,
		onRecheck = { activity?.recheckProfileAfterFailure() },
		onProfiles = { activity?.openProfilesFromProfileCheckFailed() },
		modifier = modifier,
	)
}
