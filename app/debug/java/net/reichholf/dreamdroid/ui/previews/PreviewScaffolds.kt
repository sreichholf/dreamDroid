package net.reichholf.dreamdroid.ui.previews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme

@Composable
internal fun PhonePreview(dark: Boolean, content: @Composable () -> Unit) {
    DreamDroidTheme(forceDark = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

@Composable
internal fun TvPreview(dark: Boolean = true, content: @Composable () -> Unit) {
    DreamDroidTvTheme(forceDark = dark) {
        content()
    }
}
