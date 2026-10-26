package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.LocalTvContentInset
import net.reichholf.dreamdroid.ui.theme.LocalTvFormChrome

/**
 * Material 3 compact-form metrics: 16.dp screen inset, 16.dp between fields and
 * section cards, [titleMedium] section titles, [bodyLarge] controls. Binary
 * settings are [ListItem] rows with a trailing [Switch], not a leading checkbox.
 *
 * The TV variant ([LocalTvFormChrome]) swaps in the components from `TvForm.kt`:
 * section headers instead of cards, TV rows for switches, pickers, dropdowns and
 * Save, the TV type scale, 32/24.dp insets and 20.dp between groups.
 */
object EditForm {
    val ScreenPadding = 16.dp
    val GroupSpacing = 16.dp
    val FieldSpacing = 16.dp
    val CardPadding = 16.dp
}

/** Screen inset and group spacing of the current form factor. */
private data class EditFormMetrics(
    val screenInset: Dp,
    val screenInsetVertical: Dp,
    val groupSpacing: Dp
)

private val PhoneEditFormMetrics = EditFormMetrics(
    screenInset = EditForm.ScreenPadding,
    screenInsetVertical = EditForm.ScreenPadding,
    groupSpacing = EditForm.GroupSpacing
)

/** 32/24.dp insets and 20.dp between groups, matching `TvSettingsScreen` and the hub. */
private val TvEditFormMetrics = EditFormMetrics(
    screenInset = 32.dp,
    screenInsetVertical = 24.dp,
    groupSpacing = 20.dp
)

@Composable
private fun editFormMetrics(): EditFormMetrics =
    if (LocalTvFormChrome.current) TvEditFormMetrics else PhoneEditFormMetrics

/** Control text: [bodyLarge] on phone, the TV [bodyLarge] in a TV form. */
@Composable
private fun editFormFieldTextStyle(): TextStyle {
    if (LocalTvFormChrome.current) {
        return TvMaterialTheme.typography.bodyLarge.copy(
            color = TvMaterialTheme.colorScheme.onSurface
        )
    }
    return MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
}

/** Text and dropdown fields are 72.dp tall in a TV form; the phone keeps the M3 default. */
@Composable
private fun editFormFieldHeight(): Modifier =
    if (LocalTvFormChrome.current) Modifier.heightIn(min = 72.dp) else Modifier

/**
 * Save as a [TvFormSaveButton] at the end of a TV form. Renders nothing on phone, where
 * Save lives in the shell top bar or the in-content FAB.
 */
@Composable
fun EditFormSaveRow(
    label: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!LocalTvFormChrome.current) {
        return
    }
    TvFormSaveButton(label = label, onSave = onSave, modifier = modifier, enabled = enabled)
}

@Composable
fun EditFormColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val metrics = editFormMetrics()
    // A host that already insets its content (the hub's section pane) is not inset twice.
    val hostInset = LocalTvContentInset.current
    val horizontal = (metrics.screenInset - hostInset.horizontal).coerceAtLeast(0.dp)
    val top = (metrics.screenInsetVertical - hostInset.top).coerceAtLeast(0.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = horizontal,
                top = top,
                end = horizontal,
                bottom = metrics.screenInsetVertical
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.groupSpacing),
        content = content
    )
}

@Composable
fun EditFormSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalTvFormChrome.current) {
        TvFormSection(modifier = modifier, title = title, content = content)
        return
    }
    OutlinedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EditForm.CardPadding),
            verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
        ) {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
fun EditFormSubsection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalTvFormChrome.current) {
        TvFormSubsection(modifier = modifier, title = title, content = content)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
fun EditPairedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing),
        verticalAlignment = Alignment.Top,
        content = content
    )
}

@Composable
fun EditSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    if (LocalTvFormChrome.current) {
        TvSwitchRow(
            title = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            summary = summary,
            enabled = enabled
        )
        return
    }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = summary?.let { text -> { Text(text) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch
            )
    )
}

@Composable
fun EditOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    password: Boolean = false,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: String? = null,
    contentDescription: String = label
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val tv = LocalTvFormChrome.current
    val focusManager = LocalFocusManager.current
    val resolvedTrailingIcon: @Composable (() -> Unit)? = if (password) {
        {
            PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
        }
    } else {
        trailingIcon
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { EditFormFieldLabel(label) },
        singleLine = singleLine,
        readOnly = readOnly,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { message ->
            { EditFormSupportingText(message) }
        },
        trailingIcon = resolvedTrailingIcon,
        suffix = suffix?.let { suffixText ->
            { Text(suffixText) }
        },
        modifier = modifier
            .fillMaxWidth()
            .then(if (tv) Modifier.clearFocusOnBack(focusManager) else Modifier)
            .then(editFormFieldHeight())
            .then(
                if (password) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                }
            ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (password && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        textStyle = editFormFieldTextStyle()
    )
}

/**
 * [EditOutlinedTextField] over a [TextFieldState] the caller owns, typically a
 * ViewModel. Typing edits [state] directly, with no value/onValueChange round trip.
 */
@Composable
fun EditOutlinedTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    suffix: String? = null,
    inputTransformation: InputTransformation? = null,
    enabled: Boolean = true,
    contentDescription: String = label
) {
    val tv = LocalTvFormChrome.current
    val keyboardOnSelect = rememberKeyboardOnSelect()
    val focusManager = LocalFocusManager.current
    val keyboardOptions = KeyboardOptions(
        keyboardType = keyboardType,
        imeAction = imeAction,
        showKeyboardOnFocus = keyboardOnSelect.showOnFocus
    )
    val textStyle = editFormFieldTextStyle()
    val fieldHeight = editFormFieldHeight()
    // Next moves on down the form; Done leaves the field and hides the keyboard. On
    // phone the Material 3 defaults stay.
    val keyboardAction: KeyboardActionHandler? = if (tv) {
        KeyboardActionHandler { performDefaultAction ->
            when (imeAction) {
                ImeAction.Next -> focusManager.moveFocus(FocusDirection.Down)
                ImeAction.Done -> focusManager.clearFocus()
                else -> performDefaultAction()
            }
        }
    } else {
        null
    }
    val supporting: @Composable (() -> Unit)? = supportingText?.let { message ->
        { EditFormSupportingText(message) }
    }
    val fieldModifier = modifier
        .then(keyboardOnSelect.modifier)
        .then(if (tv) Modifier.clearFocusOnBack(focusManager) else Modifier)
        .then(fieldHeight)
        .fillMaxWidth()
    if (password) {
        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedSecureTextField(
            state = state,
            label = { EditFormFieldLabel(label) },
            isError = isError,
            supportingText = supporting,
            enabled = enabled,
            trailingIcon = {
                PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
            },
            textObfuscationMode = if (passwordVisible) {
                TextObfuscationMode.Visible
            } else {
                TextObfuscationMode.RevealLastTyped
            },
            keyboardOptions = keyboardOptions,
            onKeyboardAction = keyboardAction,
            textStyle = textStyle,
            modifier = fieldModifier
        )
    } else {
        OutlinedTextField(
            state = state,
            label = { EditFormFieldLabel(label) },
            inputTransformation = inputTransformation,
            suffix = suffix?.let { suffixText ->
                { Text(suffixText) }
            },
            enabled = enabled,
            lineLimits = if (singleLine) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine()
            },
            isError = isError,
            supportingText = supporting,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = keyboardAction,
            textStyle = textStyle,
            modifier = fieldModifier.semantics { this.contentDescription = contentDescription }
        )
    }
}

/** TV fields use TV type on the label; the phone keeps the OutlinedTextField default. */
@Composable
private fun EditFormFieldLabel(label: String) {
    if (LocalTvFormChrome.current) {
        // No colour: the field's decoration supplies M3's label colours, focused included.
        TvText(text = label, style = TvMaterialTheme.typography.titleSmall)
    } else {
        Text(text = label)
    }
}

/** TV fields use TV body for the supporting line; the phone keeps the Material 3 default. */
@Composable
private fun EditFormSupportingText(message: String) {
    if (LocalTvFormChrome.current) {
        // No colour: an error supporting text has to stay in the field's error colour.
        TvText(text = message, style = TvMaterialTheme.typography.bodyMedium)
    } else {
        Text(text = message)
    }
}

/** Back on a focused TV field leaves the field instead of the editor. */
private fun Modifier.clearFocusOnBack(focusManager: FocusManager): Modifier = onPreviewKeyEvent {
    if (it.type == KeyEventType.KeyUp && (it.key == Key.Back || it.key == Key.Escape)) {
        focusManager.clearFocus()
        true
    } else {
        false
    }
}

private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private class KeyboardOnSelect(val showOnFocus: Boolean, val modifier: Modifier)

/**
 * Under D-pad input the IME opens on OK / Enter instead of on focus, so moving through a
 * form does not land in the keyboard at every field. Flipping [KeyboardOptions.showKeyboardOnFocus]
 * on a focused field restarts its input session, which shows the keyboard.
 */
@Composable
private fun rememberKeyboardOnSelect(): KeyboardOnSelect {
    val touch = LocalInputModeManager.current.inputMode == InputMode.Touch
    var selected by remember { mutableStateOf(false) }
    val modifier = remember {
        Modifier
            .onFocusChanged { if (!it.isFocused) selected = false }
            .onPreviewKeyEvent { event ->
                if (!selected && event.type == KeyEventType.KeyDown && event.key in SelectKeys) {
                    selected = true
                    true
                } else {
                    false
                }
            }
    }
    return KeyboardOnSelect(showOnFocus = touch || selected, modifier = modifier)
}

@Composable
private fun PasswordVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            painter = painterResource(
                if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            ),
            contentDescription = stringResource(
                if (visible) R.string.hide_password else R.string.show_password
            )
        )
    }
}

@Composable
fun EditPickField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalTvFormChrome.current) {
        TvFormPickRow(value = value, label = label, onClick = onClick, modifier = modifier)
        return
    }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDropdownField(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    supportingText: String? = null
) {
    if (LocalTvFormChrome.current) {
        TvFormDropdownRow(
            options = options,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            label = label,
            modifier = modifier,
            supportingText = supportingText
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selected = options.getOrElse(selectedIndex) { "" }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = supportingText?.let { message ->
                { Text(message) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .then(editFormFieldHeight())
                .semantics { this.contentDescription = contentDescription },
            textStyle = editFormFieldTextStyle()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
