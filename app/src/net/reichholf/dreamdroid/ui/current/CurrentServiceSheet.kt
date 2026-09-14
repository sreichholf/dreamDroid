package net.reichholf.dreamdroid.ui.current

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.enigma.CurrentService

/**
 * Now/next + stream as a Material 3 sheet (same host pattern as EPG detail).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentServiceSheet(
    current: CurrentService?,
    onStream: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        NowPlayingDetailScreen(
            current = current,
            loading = loading,
            onStream = {
                onStream()
                onDismiss()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
