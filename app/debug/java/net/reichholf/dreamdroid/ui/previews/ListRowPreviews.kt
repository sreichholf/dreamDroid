package net.reichholf.dreamdroid.ui.previews

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.reichholf.dreamdroid.ui.compose.ListRow

@Preview(name = "List row", widthDp = 412, heightDp = 915)
@Composable
private fun ListRowPreview() {
    PhonePreview(dark = true) {
        Column {
            ListRow(
                headlineContent = { Text(text = "Das Erste HD") },
                supportingContent = { Text(text = "Tatort · 20:15") },
                onClick = {}
            )
        }
    }
}

@Preview(name = "List row selected", widthDp = 412, heightDp = 915)
@Composable
private fun ListRowSelectedPreview() {
    PhonePreview(dark = true) {
        Column {
            ListRow(
                headlineContent = { Text(text = "Das Erste HD") },
                supportingContent = { Text(text = "Tatort · 20:15") },
                onClick = {},
                selected = true
            )
        }
    }
}
