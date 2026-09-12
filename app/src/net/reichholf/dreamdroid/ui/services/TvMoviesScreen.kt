package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DestinationBar
import net.reichholf.dreamdroid.ui.nav.DestinationBarItem

@Composable
fun TvMoviesScreen(
	selected: TvMoviesDestination,
	rows: List<String>,
	selectedRow: Int,
	error: String?,
	onDestinationSelected: (TvMoviesDestination) -> Unit,
	onRowSelected: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier.fillMaxWidth()) {
		TvMoviesHeader(
			rows = rows,
			selectedRow = selectedRow,
			error = error,
			onRowSelected = onRowSelected,
		)
		TvMoviesDestinationBar(
			selected = selected,
			onDestinationSelected = onDestinationSelected,
		)
	}
}

@Composable
fun TvMoviesHeader(
	rows: List<String>,
	selectedRow: Int,
	error: String?,
	onRowSelected: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier.fillMaxWidth()) {
		if (!error.isNullOrBlank()) {
			Text(
				text = error,
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
		}
		if (rows.isNotEmpty()) {
			val tabIndex = selectedRow.coerceIn(0, rows.lastIndex)
			ScrollableTabRow(selectedTabIndex = tabIndex) {
				rows.forEachIndexed { index, title ->
					Tab(
						selected = index == tabIndex,
						onClick = { onRowSelected(index) },
						text = { Text(title) },
					)
				}
			}
		}
	}
}

@Composable
fun TvMoviesDestinationBar(
	selected: TvMoviesDestination,
	onDestinationSelected: (TvMoviesDestination) -> Unit,
	modifier: Modifier = Modifier,
) {
	DestinationBar(
		items = TvMoviesDestination.entries.map { dest ->
			DestinationBarItem(
				labelRes = destinationLabelRes(dest),
				iconRes = destinationIcon(dest),
			)
		},
		selectedIndex = selected.ordinal,
		onSelect = { onDestinationSelected(TvMoviesDestination.entries[it]) },
		modifier = modifier,
	)
}

private fun destinationIcon(dest: TvMoviesDestination): Int {
	return when (dest) {
		TvMoviesDestination.TV -> R.drawable.ic_menu_tv
		TvMoviesDestination.RADIO -> R.drawable.ic_menu_radio
		TvMoviesDestination.MOVIES -> R.drawable.ic_menu_movie
		TvMoviesDestination.TIMER -> R.drawable.ic_menu_timer
	}
}

private fun destinationLabelRes(dest: TvMoviesDestination): Int {
	return when (dest) {
		TvMoviesDestination.TV -> R.string.tv
		TvMoviesDestination.RADIO -> R.string.radio
		TvMoviesDestination.MOVIES -> R.string.movies
		TvMoviesDestination.TIMER -> R.string.timer
	}
}
