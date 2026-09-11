package net.reichholf.dreamdroid.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.dialogs.DreamDroidAttributionPresenter

data class AboutContent(
    val title: String,
    val version: String,
    val license: String,
    val sourceLink: String,
    val licensesLabel: String,
)

@Composable
fun rememberAboutContent(): AboutContent {
    return AboutContent(
        title = stringResource(R.string.about),
        version = DreamDroid.VERSION_STRING,
        license = stringResource(R.string.license_gplv3),
        sourceLink = stringResource(R.string.source_code_link),
        licensesLabel = stringResource(R.string.licenses),
    )
}

@Composable
fun AboutScreen(
    content: AboutContent,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = content.version,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = content.license,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SourceLinkText(sourceLink = content.sourceLink)
        TextButton(onClick = onLicensesClick) {
            Text(content.licensesLabel)
        }
    }
}

/**
 * Phase 2.1g-ii-b: Material 3 [AlertDialog] About (no DialogFragment / AlertDialogBuilder host).
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    content: AboutContent = rememberAboutContent(),
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content.title) },
        text = {
            AboutScreen(
                content = content,
                onLicensesClick = {
                    DreamDroidAttributionPresenter.newInstance(context)
                        .showDialog(content.licensesLabel)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun SourceLinkText(sourceLink: String) {
    val uriHandler = LocalUriHandler.current
    val httpIndex = sourceLink.indexOf("http")
    if (httpIndex < 0) {
        Text(text = sourceLink, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val url = sourceLink.substring(httpIndex).trim()
    val annotated = buildAnnotatedString {
        append(sourceLink.substring(0, httpIndex))
        pushStringAnnotation(tag = "URL", annotation = url)
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append(url)
        }
        pop()
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
}
