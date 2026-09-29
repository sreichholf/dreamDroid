package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event

const val EPG_SEARCH_FIELD_TAG = "epg_search_field"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgSearchScreen(
    queryState: TextFieldState,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<Event>,
    onItemClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    piconsEnabled: Boolean = false
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    state = queryState,
                    onSearch = onSearch,
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    modifier = Modifier.testTag(EPG_SEARCH_FIELD_TAG),
                    placeholder = {
                        Text(stringResource(R.string.epg_search_hint))
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_search),
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (queryState.text.isNotEmpty()) {
                        {
                            IconButton(onClick = { queryState.clearText() }) {
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
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
        }
        if (!expanded) {
            EpgBouquetScreen(
                items = items,
                onItemClick = onItemClick,
                emptyMessage = emptyMessage,
                piconsEnabled = piconsEnabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
