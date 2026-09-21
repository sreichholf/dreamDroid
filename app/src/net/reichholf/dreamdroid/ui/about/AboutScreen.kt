package net.reichholf.dreamdroid.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

data class AboutContent(
    val title: String,
    val version: String,
    val license: String,
    val sourceLink: String,
    val licensesLabel: String
)

@Composable
fun rememberAboutContent(): AboutContent = AboutContent(
    title = stringResource(R.string.about),
    version = DreamDroid.VERSION_STRING,
    license = stringResource(R.string.license_gplv3),
    sourceLink = stringResource(R.string.source_code_link),
    licensesLabel = stringResource(R.string.licenses)
)

@Composable
fun AboutScreen(content: AboutContent, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = content.version,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = content.license,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        SourceLinkText(sourceLink = content.sourceLink)
    }
}

/**
 * Phase 2.1g-ii-b: Material 3 [AlertDialog] About (no DialogFragment / AlertDialogBuilder host).
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, content: AboutContent = rememberAboutContent()) {
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(content.title) },
            text = { AboutScreen(content = content) },
            dismissButton = {
                TextButton(onClick = { showLicenses = true }) {
                    Text(content.licensesLabel)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SourceLinkText(sourceLink: String) {
    val httpIndex = sourceLink.indexOf("http")
    if (httpIndex < 0) {
        Text(text = sourceLink, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val url = sourceLink.substring(httpIndex).trim()
    val annotated = buildAnnotatedString {
        append(sourceLink.substring(0, httpIndex))
        withLink(
            LinkAnnotation.Url(
                url,
                TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) {
            append(url)
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}
