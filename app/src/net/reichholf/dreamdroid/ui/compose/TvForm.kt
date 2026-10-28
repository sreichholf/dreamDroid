package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text as PhoneText
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text

/** A TV form section: a heading over its fields, no card, 16.dp between the fields. */
@Composable
fun TvFormSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
    ) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = TvMaterialTheme.typography.titleMedium,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
        }
        content()
    }
}

@Composable
fun TvFormSubsection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
    ) {
        Text(
            text = title,
            style = TvMaterialTheme.typography.titleSmall,
            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

/** A value with a picker: the whole row is the button, as on every other TV list. */
@Composable
fun TvFormPickRow(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvListRow(
        title = label,
        summary = value,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button }
    )
}

@Composable
fun TvFormDropdownRow(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvFormPickRow(
            value = options.getOrElse(selectedIndex) { "" },
            label = label,
            onClick = { expanded = true }
        )
        if (!supportingText.isNullOrEmpty()) {
            Text(
                text = supportingText,
                style = TvMaterialTheme.typography.bodySmall,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
    if (expanded) {
        TvFormChoiceDialog(
            title = label,
            options = options,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            onDismiss = { expanded = false }
        )
    }
}

/**
 * Single choice out of [options]. The dialog shell is the Material 3 [AlertDialog] the TV
 * settings dialogs use; the options are TV rows, so OK picks one and the selected row
 * stays in the selection colour at rest.
 */
@Composable
fun TvFormChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PhoneText(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    TvListRow(
                        title = option,
                        summary = null,
                        onClick = {
                            onSelected(index)
                            onDismiss()
                        },
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        },
                        restContainer = if (selected) {
                            TvMaterialTheme.colorScheme.secondaryContainer
                        } else {
                            null
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                PhoneText(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun TvFormSaveButton(
    label: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    // The form clips at its viewport. Focus scrolling stops once the button's bounds are
    // visible, so the room below it (the focus scale) would be cut: scroll the padded wrapper
    // into view instead, which asks for that room too.
    LaunchedEffect(focused) {
        if (focused) {
            withFrameNanos {}
            bringIntoView.bringIntoView()
        }
    }
    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .padding(bottom = SaveFocusInset)
    ) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .onFocusChanged { focused = it.isFocused }
        ) {
            Text(text = label)
        }
    }
}

/** Room below the Save pill for its 10% focus scale, which the form's clip would otherwise cut. */
private val SaveFocusInset = 8.dp
