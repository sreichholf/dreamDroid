package net.reichholf.dreamdroid.ui.dialogs

import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

@Composable
fun ChangelogScreen(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(onSurface.toArgb())
                setPadding(24, 16, 24, 16)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        update = { view ->
            view.setTextColor(onSurface.toArgb())
            Markwon.create(context).setMarkdown(view, markdown)
        },
    )
}

fun ComposeView.bindChangelogScreen(markdown: String) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        DreamDroidTheme {
            ChangelogScreen(markdown = markdown)
        }
    }
}
