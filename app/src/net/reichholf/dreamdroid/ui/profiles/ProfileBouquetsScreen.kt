package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.compose.EditFormColumn
import net.reichholf.dreamdroid.ui.compose.EditFormSection
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.TvButton
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString
import net.reichholf.dreamdroid.ui.theme.LocalTvFormChrome

const val BOUQUET_VISIBILITY_ROW_TAG_PREFIX = "bouquet_visibility_"
const val BOUQUET_VISIBILITY_SHOW_ALL_TAG = "bouquet_visibility_show_all"

/**
 * A profile's TV and radio bouquets, each with the switch that shows or hides it in every
 * bouquet list. The profile stores the hidden references, so a bouquet the receiver adds
 * later is visible by default.
 */
@Composable
fun ProfileBouquetsScreen(
    state: ProfileBouquetsUiState,
    onToggle: (Service, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tvForm = LocalTvFormChrome.current
    val hasRows = state.tv.isNotEmpty() || state.radio.isNotEmpty()
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(tvForm, state.loading, hasRows) {
        if (tvForm && hasRows && !state.loading) {
            withFrameNanos {}
            firstRowFocus.requestFocus()
        }
    }

    when {
        state.loading && !hasRows -> ListEmptyState(
            loading = true,
            message = null,
            modifier = modifier
        )

        !hasRows && state.hidden.isEmpty() -> ListEmptyState(
            loading = false,
            message = (state.error ?: UiText.Resource(R.string.no_bouquets_on_receiver))
                .asString(),
            onRetry = onReload.takeIf { state.error != null },
            modifier = modifier
        )

        else -> EditFormColumn(modifier = modifier) {
            if (state.profileName.isNotEmpty()) {
                Text(
                    text = state.profileName,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = stringResource(R.string.bouquets_visibility_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let { error ->
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.hidden.isNotEmpty()) {
                if (tvForm) {
                    TvButton(
                        label = stringResource(R.string.show_all_bouquets),
                        onClick = onShowAll,
                        modifier = Modifier.testTag(BOUQUET_VISIBILITY_SHOW_ALL_TAG)
                    )
                } else {
                    FilledTonalButton(
                        onClick = onShowAll,
                        modifier = Modifier.testTag(BOUQUET_VISIBILITY_SHOW_ALL_TAG)
                    ) {
                        Text(stringResource(R.string.show_all_bouquets))
                    }
                }
            }
            if (state.tv.isNotEmpty()) {
                val first = state.tv.first()
                EditFormSection(title = stringResource(R.string.tv)) {
                    state.tv.forEach { service ->
                        BouquetVisibilityRow(
                            service = service,
                            visible = service.reference !in state.hidden,
                            onToggle = onToggle,
                            modifier = if (service.reference == first.reference) {
                                Modifier.focusRequester(firstRowFocus)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
            if (state.radio.isNotEmpty()) {
                val first = state.radio.first()
                EditFormSection(title = stringResource(R.string.radio)) {
                    state.radio.forEach { service ->
                        BouquetVisibilityRow(
                            service = service,
                            visible = service.reference !in state.hidden,
                            onToggle = onToggle,
                            modifier = if (
                                state.tv.isEmpty() && service.reference == first.reference
                            ) {
                                Modifier.focusRequester(firstRowFocus)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BouquetVisibilityRow(
    service: Service,
    visible: Boolean,
    onToggle: (Service, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    EditSwitchRow(
        checked = visible,
        onCheckedChange = { onToggle(service, it) },
        label = service.name.ifBlank { stringResource(R.string.bouquets) },
        summary = stringResource(
            if (visible) R.string.bouquet_shown else R.string.bouquet_hidden
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(BOUQUET_VISIBILITY_ROW_TAG_PREFIX + service.reference)
    )
}
