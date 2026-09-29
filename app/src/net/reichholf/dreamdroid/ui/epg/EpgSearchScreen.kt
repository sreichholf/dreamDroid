package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.text.asString

const val EPG_SEARCH_FIELD_TAG = "epg_search_field"
const val EPG_SEARCH_PROGRESS_TAG = "epg_search_progress"

/**
 * EPG search with the search field as the screen's top bar. Below it: recent searches
 * while the field holds too little to search, otherwise the results grouped by day. With
 * [focusOnStart] the field takes focus and opens the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgSearchScreen(
    queryState: TextFieldState,
    state: EpgSearchUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onItemClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    focusOnStart: Boolean = false
) {
    var editing by rememberSaveable { mutableStateOf(focusOnStart) }
    val focusRequester = remember { FocusRequester() }
    var focusedOnStart by rememberSaveable { mutableStateOf(false) }
    if (focusOnStart && !focusedOnStart) {
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
            focusedOnStart = true
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            shape = SearchBarDefaults.inputFieldShape,
            color = SearchBarDefaults.colors().containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SearchBarDefaults.InputField(
                state = queryState,
                onSearch = {
                    editing = false
                    onSearch()
                },
                expanded = editing,
                onExpandedChange = { editing = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(EPG_SEARCH_FIELD_TAG),
                placeholder = { Text(stringResource(R.string.epg_search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.epg_search_back)
                        )
                    }
                },
                trailingIcon = if (queryState.text.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            queryState.clearText()
                            editing = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_close),
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                } else {
                    null
                }
            )
        }
        if (state.searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EPG_SEARCH_PROGRESS_TAG)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.showRecent) {
                RecentSearches(
                    recent = state.recentSearches,
                    onClick = { query ->
                        editing = false
                        onRecentClick(query)
                    },
                    onRemove = onRecentRemove
                )
            } else {
                SearchResults(state = state, onItemClick = onItemClick, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    if (recent.isEmpty()) {
        Text(
            text = stringResource(R.string.epg_search_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.epg_search_recent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(recent, key = { it }) { query ->
            ListItem(
                headlineContent = {
                    Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(query) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_close),
                            contentDescription = stringResource(R.string.epg_search_recent_remove)
                        )
                    }
                },
                modifier = Modifier.clickable { onClick(query) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResults(
    state: EpgSearchUiState,
    onItemClick: (Event) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.cached) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.epg_search_cached_hint),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        if (state.sections.isEmpty()) {
            ListEmptyState(
                loading = false,
                message = state.emptyMessage?.asString(),
                onRetry = if (state.retryable) onRetry else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            state.sections.forEachIndexed { index, section ->
                val day = section.day
                if (day != null) {
                    stickyHeader(key = "day:$index") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = day.asString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                items(
                    section.events,
                    key = { "${it.serviceReference}:${it.eventId}:${it.start}:${it.title}" }
                ) { event ->
                    EpgBouquetRow(
                        event = event,
                        piconsEnabled = state.piconsEnabled,
                        onClick = { onItemClick(event) },
                        showDate = day == null
                    )
                }
            }
        }
    }
}
