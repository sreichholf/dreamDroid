package net.reichholf.dreamdroid.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.reichholf.dreamdroid.ui.profiles.ProfilesScreen

@Preview(name = "Profiles dark", widthDp = 412, heightDp = 915)
@Composable
private fun ProfilesScreenDarkPreview() {
    PhonePreview(dark = true) {
        ProfilesScreen(
            profiles = previewProfiles,
            onProfileClick = {},
            onProfileEdit = {},
            onProfileBouquets = {}
        )
    }
}

@Preview(name = "Profiles light", widthDp = 412, heightDp = 915)
@Composable
private fun ProfilesScreenLightPreview() {
    PhonePreview(dark = false) {
        ProfilesScreen(
            profiles = previewProfiles,
            onProfileClick = {},
            onProfileEdit = {},
            onProfileBouquets = {}
        )
    }
}
