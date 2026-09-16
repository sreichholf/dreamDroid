package net.reichholf.dreamdroid.ui.pick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

@Composable
fun PickServiceScreen(
    items: List<Service>,
    onItemClick: (Service) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null
) {
    val loadingLabel = stringResource(R.string.loading)
    if (items.isEmpty()) {
        ListEmptyState(
            loading = emptyMessage == loadingLabel,
            message = emptyMessage,
            modifier = modifier
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { "${it.reference}:${it.name}" }) { service ->
            BouquetRow(
                name = service.name,
                onClick = { onItemClick(service) }
            )
        }
    }
}

@Composable
private fun BouquetRow(name: String, onClick: () -> Unit) {
    ListRowSurface(modifier = Modifier.clickable(onClick = onClick)) {
        ListItem(
            headlineContent = {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            colors = listRowItemColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
