package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

data class PowerChoiceItem(
    val id: Int,
    val label: String,
)

@Composable
fun PowerStateScreen(
    items: List<PowerChoiceItem>,
    onItemClick: (PowerChoiceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        items.forEach { item ->
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

fun ComposeView.bindPowerStateScreen(
    items: List<PowerChoiceItem>,
    onItemClick: (PowerChoiceItem) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        DreamDroidTheme {
            PowerStateScreen(items = items, onItemClick = onItemClick)
        }
    }
}
