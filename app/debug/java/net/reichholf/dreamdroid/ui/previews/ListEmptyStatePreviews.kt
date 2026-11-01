package net.reichholf.dreamdroid.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.reichholf.dreamdroid.ui.compose.ListEmptyState

@Preview(name = "List loading", widthDp = 412, heightDp = 915)
@Composable
private fun ListEmptyStateLoadingPreview() {
    PhonePreview(dark = true) {
        ListEmptyState(loading = true, message = null)
    }
}

@Preview(name = "List empty", widthDp = 412, heightDp = 915)
@Composable
private fun ListEmptyStateEmptyPreview() {
    PhonePreview(dark = true) {
        ListEmptyState(loading = false, message = "No items to display…", onRetry = {})
    }
}
