package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.compose.ListSectionHeader
import net.reichholf.dreamdroid.ui.text.asString

/**
 * Bouquets with a checkbox each (the whole bouquet) that open on a tap, or a bouquet's
 * channels, which a tap picks. Markers of the bouquet list show as headers.
 */
@Composable
fun AutoTimerTargetPickScreen(
    state: AutoTimerTargetPickUiState,
    onRefresh: () -> Unit,
    onOpen: (Service) -> Unit,
    onToggle: (Service) -> Unit,
    modifier: Modifier = Modifier
) {
    DreamDroidPullRefresh(
        refreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        if (state.rows.isEmpty()) {
            ListEmptyState(
                loading = state.refreshing,
                message = state.emptyMessage?.asString()
            )
        } else {
            val pickBouquet = stringResource(R.string.autotimer_pick_bouquet)
            LazyColumn {
                itemsIndexed(state.rows, key = ::rowKey) { _, row ->
                    when {
                        ServiceKeys.isMarker(row.reference) -> ListSectionHeader(row.name)

                        state.bouquet == null -> TargetRow(
                            row = row,
                            checked = state.isSelected(row),
                            onCheckedChange = { onToggle(row) },
                            checkboxLabel = pickBouquet,
                            modifier = Modifier.clickable(role = Role.Button) { onOpen(row) }
                        )

                        else -> TargetRow(
                            row = row,
                            checked = state.isSelected(row),
                            onCheckedChange = null,
                            checkboxLabel = null,
                            modifier = Modifier.toggleable(
                                value = state.isSelected(row),
                                role = Role.Checkbox,
                                onValueChange = { onToggle(row) }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetRow(
    row: Service,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    checkboxLabel: String?,
    modifier: Modifier
) {
    ListRow(
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = if (checkboxLabel != null) {
                    Modifier.semantics { contentDescription = "$checkboxLabel ${row.name}" }
                } else {
                    Modifier
                }
            )
        },
        headlineContent = {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = modifier
    )
}

/** Two markers may share their text, and with it their reference. */
private fun rowKey(index: Int, row: Service): String = if (ServiceKeys.isMarker(row.reference)) {
    "$index:${row.reference}"
} else {
    row.reference
}
