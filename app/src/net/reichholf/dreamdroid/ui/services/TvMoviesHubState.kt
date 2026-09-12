package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Snapshot state for the TV & Movies hub chrome (header tabs + shell destination bar).
 * The shell bar is published through [net.reichholf.dreamdroid.ui.nav.RegisterShellDestinationBar].
 */
class TvMoviesHubState {
	var selected by mutableStateOf(TvMoviesDestination.TV)
	var rows by mutableStateOf(listOf<String>())
	var selectedRow by mutableIntStateOf(0)
	var error by mutableStateOf<String?>(null)
	/** Latest hub destination click handler; shell composition reads this on each click. */
	var onDestinationSelected: (TvMoviesDestination) -> Unit = {}
}
