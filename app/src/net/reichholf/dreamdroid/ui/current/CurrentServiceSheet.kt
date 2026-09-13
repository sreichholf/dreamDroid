package net.reichholf.dreamdroid.ui.current

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment

/**
 * Full current-service detail (now/next/stream) as a sheet from the hub now-playing strip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentServiceSheet(
    hostFragment: PhoneNavHostFragment,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CurrentServiceDestination(
            hostFragment = hostFragment,
            updateToolbarTitle = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}
