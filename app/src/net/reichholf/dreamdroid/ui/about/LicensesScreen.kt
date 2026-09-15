package net.reichholf.dreamdroid.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

data class ThirdPartyLicense(
    val name: String,
    val copyright: String,
    val license: String,
    val website: String
)

fun dreamDroidLicenses(): List<ThirdPartyLicense> = listOf(
    ThirdPartyLicense(
        name = "AndroidX",
        copyright = "Copyright (C) The Android Open Source Project",
        license = "Apache License 2.0",
        website = "https://developer.android.com/jetpack/androidx/"
    ),
    ThirdPartyLicense(
        name = "Coil",
        copyright = "Copyright 2025 Coil Contributors",
        license = "Apache License 2.0",
        website = "https://github.com/coil-kt/coil"
    ),
    ThirdPartyLicense(
        name = "Gson",
        copyright = "Copyright 2008 Google Inc.",
        license = "Apache License 2.0",
        website = "https://github.com/google/gson"
    ),
    ThirdPartyLicense(
        name = "JmDNS",
        copyright = "Copyright (C) 2018 JmDNS.org and others",
        license = "Apache License 2.0",
        website = "https://github.com/jmdns/jmdns"
    ),
    ThirdPartyLicense(
        name = "libVLC",
        copyright = "Copyright (C) VLC authors and VideoLAN",
        license = "GNU Lesser General Public License, version 2.1",
        website = "https://code.videolan.org/videolan/libvlc-android"
    ),
    ThirdPartyLicense(
        name = "OkHttp",
        copyright = "Copyright 2019 Square, Inc.",
        license = "Apache License 2.0",
        website = "https://square.github.io/okhttp/"
    ),
    ThirdPartyLicense(
        name = "Apache Commons Net",
        copyright = "Copyright 2001-2024 The Apache Software Foundation",
        license = "Apache License 2.0",
        website = "https://commons.apache.org/proper/commons-net/"
    )
)

@Composable
fun LicensesDialog(
    onDismiss: () -> Unit,
    licenses: List<ThirdPartyLicense> = dreamDroidLicenses()
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.licenses)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                licenses.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(entry.website) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = entry.copyright,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = entry.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
