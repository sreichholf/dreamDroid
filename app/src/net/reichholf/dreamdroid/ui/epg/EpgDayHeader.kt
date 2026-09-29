package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString

/** Sticky header over one [EpgDaySection]; opaque so rows scroll under it. */
@Composable
internal fun EpgDayHeader(day: UiText) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = day.asString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
