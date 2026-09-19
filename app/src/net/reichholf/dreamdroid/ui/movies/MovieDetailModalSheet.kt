package net.reichholf.dreamdroid.ui.movies

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 2.1g-ii-d: Material 3 [ModalBottomSheet] host for movie detail
 * (no BottomSheetDialogFragment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailModalSheet(
    content: MovieDetailContent,
    onDismiss: () -> Unit,
    /** Phone bottom sheet caps height; TV overlay/fullscreen passes null. */
    heightCap: Dp? = 480.dp
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        MovieDetailScreen(
            content = content,
            heightCap = heightCap,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
