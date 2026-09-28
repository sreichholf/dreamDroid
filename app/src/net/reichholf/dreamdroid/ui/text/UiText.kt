package net.reichholf.dreamdroid.ui.text

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources

/**
 * Text in UI state, resolved in the UI. [Resource] args that are themselves [UiText]
 * resolve first. [Raw] carries text that has no resource, such as a receiver's own
 * error string.
 */
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) :
        UiText

    data class Raw(val text: String) : UiText
}

fun UiText.asString(resources: Resources): String = when (this) {
    is UiText.Raw -> text

    is UiText.Resource -> if (args.isEmpty()) {
        resources.getString(id)
    } else {
        val resolved = args.map { arg -> if (arg is UiText) arg.asString(resources) else arg }
        resources.getString(id, *resolved.toTypedArray())
    }
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = asString(LocalResources.current)
