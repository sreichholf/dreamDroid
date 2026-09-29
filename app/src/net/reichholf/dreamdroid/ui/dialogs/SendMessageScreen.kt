package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.EditDropdownField
import net.reichholf.dreamdroid.ui.compose.EditForm
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField

@Composable
fun SendMessageScreen(
    message: TextFieldState,
    timeout: TextFieldState,
    typeIndex: Int,
    onTypeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = stringArrayResource(R.array.message_types)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
    ) {
        EditOutlinedTextField(
            state = message,
            label = stringResource(R.string.message_text_hint),
            singleLine = false
        )
        EditDropdownField(
            options = types.toList(),
            selectedIndex = typeIndex,
            onSelected = onTypeSelected,
            label = stringResource(R.string.type)
        )
        EditOutlinedTextField(
            state = timeout,
            label = stringResource(R.string.timeout),
            keyboardType = KeyboardType.Number,
            suffix = stringResource(R.string.seconds),
            inputTransformation = SendMessageViewModel.TimeoutInput
        )
    }
}
