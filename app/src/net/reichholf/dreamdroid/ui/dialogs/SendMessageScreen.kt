package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.EditDropdownField
import net.reichholf.dreamdroid.ui.compose.EditForm
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class SendMessageUiState(
    initialMessage: String = "",
    initialTypeIndex: Int = 2,
    initialTimeout: String = "20"
) {
    var message by mutableStateOf(initialMessage)
    var typeIndex by mutableIntStateOf(initialTypeIndex)
    var timeout by mutableStateOf(initialTimeout)
}

@Composable
fun SendMessageScreen(state: SendMessageUiState, modifier: Modifier = Modifier) {
    val types = stringArrayResource(R.array.message_types)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
    ) {
        EditOutlinedTextField(
            value = state.message,
            onValueChange = { state.message = it },
            label = stringResource(R.string.message_text_hint),
            singleLine = false
        )
        EditDropdownField(
            options = types.toList(),
            selectedIndex = state.typeIndex,
            onSelected = { state.typeIndex = it },
            label = stringResource(R.string.type)
        )
        EditOutlinedTextField(
            value = state.timeout,
            onValueChange = { value ->
                if (value.length <= 2 && value.all { it.isDigit() }) {
                    state.timeout = value
                }
            },
            label = stringResource(R.string.timeout),
            keyboardType = KeyboardType.Number,
            suffix = stringResource(R.string.seconds)
        )
    }
}

fun ComposeView.bindSendMessageScreen(state: SendMessageUiState) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        DreamDroidTheme {
            SendMessageScreen(state = state)
        }
    }
}
