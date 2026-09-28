package net.reichholf.dreamdroid.ui.compose

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** One entry of a list row's action menu. */
interface RowMenuAction {
    @get:StringRes
    val label: Int
}

/** The open action menu: which row it belongs to and the actions it offers. */
data class RowMenuState<A : RowMenuAction>(val rowKey: Any, val actions: List<A>)

/**
 * Material 3 menu anchored to the row it is placed next to. Put it in the same [androidx.compose.foundation.layout.Box]
 * as the row; it shows while [state] targets [rowKey].
 */
@Composable
fun <A : RowMenuAction> RowMenu(
    rowKey: Any,
    state: RowMenuState<A>?,
    onAction: (A) -> Unit,
    onDismiss: () -> Unit
) {
    val open = state?.rowKey == rowKey
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (state != null && open) {
            state.actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    onClick = {
                        onDismiss()
                        onAction(action)
                    }
                )
            }
        }
    }
}
