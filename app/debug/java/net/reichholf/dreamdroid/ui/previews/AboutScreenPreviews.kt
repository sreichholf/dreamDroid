package net.reichholf.dreamdroid.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.reichholf.dreamdroid.ui.about.AboutScreen

@Preview(name = "About", widthDp = 412, heightDp = 915)
@Composable
private fun AboutScreenPreview() {
    PhonePreview(dark = true) {
        AboutScreen(content = previewAbout())
    }
}
