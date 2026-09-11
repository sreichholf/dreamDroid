package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Phase 2.1g-ii-d: Material 3 [ModalBottomSheet] host for EPG detail
 * (no BottomSheetDialogFragment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgDetailModalSheet(
    content: EpgDetailContent,
    onDismiss: () -> Unit,
    onSetTimer: () -> Unit,
    onEditTimer: () -> Unit,
    onImdb: () -> Unit,
    onSimilar: () -> Unit,
    showActions: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        EpgDetailScreen(
            content = content,
            onSetTimer = {
                onSetTimer()
                onDismiss()
            },
            onEditTimer = {
                onEditTimer()
                onDismiss()
            },
            onImdb = {
                onImdb()
                onDismiss()
            },
            onSimilar = {
                onSimilar()
                onDismiss()
            },
            showActions = showActions,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}
