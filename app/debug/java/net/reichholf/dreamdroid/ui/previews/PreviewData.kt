package net.reichholf.dreamdroid.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.about.AboutContent
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem

internal val previewProfiles = listOf(
    ProfileListItem(id = 1, name = "Living Room", host = "dm7080.local", active = true),
    ProfileListItem(id = 2, name = "Bedroom", host = "192.168.1.50", active = false),
    ProfileListItem(
        id = 3,
        name = "Wohnzimmer Dreambox (DM7080HD)",
        host = "dreamdroid.example.org:8080",
        active = false
    )
)

@Composable
internal fun previewAbout(): AboutContent = AboutContent(
    title = stringResource(R.string.about),
    version = DreamDroid.getVersionString(),
    license = stringResource(R.string.license_gplv3),
    sourceLink = stringResource(R.string.source_code_link),
    licensesLabel = stringResource(R.string.licenses)
)
