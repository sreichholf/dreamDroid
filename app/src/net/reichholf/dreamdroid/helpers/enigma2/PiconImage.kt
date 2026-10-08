package net.reichholf.dreamdroid.helpers.enigma2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import net.reichholf.dreamdroid.R

/**
 * Compose picon loaded with Coil; [PiconImageLoader] picks the current profile's online or
 * synced picon. Hidden when picons are disabled or the service has neither reference nor
 * name. Failed loads show [R.drawable.dreamdroid_logo_simple].
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
    if (!Picon.enabled(context) || (reference == null && name == null)) {
        return
    }
    AsyncImage(
        model = PiconKey(reference, name),
        contentDescription = contentDescription,
        error = painterResource(R.drawable.dreamdroid_logo_simple),
        contentScale = ContentScale.Fit,
        modifier = modifier,
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke() }
    )
}
