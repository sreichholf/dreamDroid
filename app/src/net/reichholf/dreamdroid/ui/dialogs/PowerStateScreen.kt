package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

data class PowerChoiceItem(val id: Int, val label: String)

@Composable
fun PowerStateScreen(
    items: List<PowerChoiceItem>,
    onItemClick: (PowerChoiceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            val destructive = item.id == Statics.ITEM_SHUTDOWN || item.id == Statics.ITEM_REBOOT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = false,
                        role = Role.RadioButton,
                        onClick = { onItemClick(item) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = false, onClick = null)
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

fun ComposeView.bindPowerStateScreen(
    items: List<PowerChoiceItem>,
    onItemClick: (PowerChoiceItem) -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        DreamDroidTheme {
            PowerStateScreen(items = items, onItemClick = onItemClick)
        }
    }
}
