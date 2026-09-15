package net.reichholf.dreamdroid.helpers.enigma2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import net.reichholf.dreamdroid.R

/**
 * Compose picon loaded with Coil. Hidden when picons are disabled or the URI
 * cannot be resolved. Failed loads show [R.drawable.dreamdroid_logo_simple].
 */
@Composable
fun PiconImage(
    reference: String?,
    name: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = name,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    PiconImageLoader.install(context)
    val uri = Picon.resolveLoadUri(context, reference, name) ?: return
    AsyncImage(
        model = uri,
        contentDescription = contentDescription,
        error = painterResource(R.drawable.dreamdroid_logo_simple),
        contentScale = ContentScale.Fit,
        modifier = modifier,
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke() }
    )
}
