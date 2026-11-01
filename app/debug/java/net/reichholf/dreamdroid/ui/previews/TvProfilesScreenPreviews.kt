package net.reichholf.dreamdroid.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import net.reichholf.dreamdroid.tv.ui.TvProfilesScreen

@Preview(name = "TV profiles", device = Devices.TV_1080p)
@Composable
private fun TvProfilesScreenPreview() {
    TvPreview(dark = true) {
        TvProfilesScreen(
            profiles = previewProfiles,
            onAdd = {},
            onBackup = {},
            onActivate = {},
            onEdit = {},
            onBouquets = {},
            onDelete = {},
            onDeleteConfirmed = {}
        )
    }
}
