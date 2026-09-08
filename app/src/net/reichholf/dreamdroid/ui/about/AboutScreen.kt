package net.reichholf.dreamdroid.ui.about

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.dialogs.DreamDroidAttributionPresenter
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

data class AboutContent(
    val title: String,
    val version: String,
    val license: String,
    val sourceLink: String,
    val licensesLabel: String,
)

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
            )
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

class AboutComposeDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = this
        val aboutContent = AboutContent(
            title = getString(R.string.about),
            version = DreamDroid.VERSION_STRING,
            license = getString(R.string.license_gplv3),
            sourceLink = getString(R.string.source_code_link),
            licensesLabel = getString(R.string.licenses),
        )
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DreamDroidTheme {
                    AboutScreen(
                        content = aboutContent,
                        onLicensesClick = {
                            DreamDroidAttributionPresenter.newInstance(requireContext())
                                .showDialog(getString(R.string.licenses))
                        },
                    )
                }
            }
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about)
            .setView(composeView)
            .setCancelable(true)
            .create()
    }

    companion object {
        @JvmStatic
        fun newInstance(): AboutComposeDialog {
            return AboutComposeDialog()
        }
    }
}
