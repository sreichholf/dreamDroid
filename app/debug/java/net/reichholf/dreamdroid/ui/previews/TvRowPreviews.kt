package net.reichholf.dreamdroid.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.TvListRow
import net.reichholf.dreamdroid.ui.compose.TvSwitchRow

@Preview(name = "TV rows", device = Devices.TV_1080p)
@Composable
private fun TvRowPreview() {
    TvPreview(dark = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvListRow(title = "Das Erste HD", summary = "Tatort · 20:15", onClick = {})
            TvSwitchRow(
                title = stringResource(R.string.dynamic_theme_colors),
                checked = true,
                onCheckedChange = {}
            )
        }
    }
}
