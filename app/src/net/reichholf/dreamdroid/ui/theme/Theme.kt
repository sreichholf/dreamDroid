package net.reichholf.dreamdroid.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

@Composable
fun DreamDroidTheme(content: @Composable () -> Unit) {
    val dark = isDreamDroidDark(LocalContext.current)
    MaterialTheme(
        colorScheme = if (dark) dreamDroidDarkColorScheme() else dreamDroidLightColorScheme(),
        content = content,
    )
}

fun isDreamDroidDark(context: Context): Boolean {
    return when (DreamDroid.getThemeType(context)) {
        0 -> false
        1 -> true
        else -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

@Composable
private fun dreamDroidLightColorScheme() = lightColorScheme(
    primary = colorResource(R.color.md_theme_light_primary),
    onPrimary = colorResource(R.color.md_theme_light_onPrimary),
    primaryContainer = colorResource(R.color.md_theme_light_primaryContainer),
    onPrimaryContainer = colorResource(R.color.md_theme_light_onPrimaryContainer),
    secondary = colorResource(R.color.md_theme_light_secondary),
    onSecondary = colorResource(R.color.md_theme_light_onSecondary),
    secondaryContainer = colorResource(R.color.md_theme_light_secondaryContainer),
    onSecondaryContainer = colorResource(R.color.md_theme_light_onSecondaryContainer),
    tertiary = colorResource(R.color.md_theme_light_tertiary),
    onTertiary = colorResource(R.color.md_theme_light_onTertiary),
    tertiaryContainer = colorResource(R.color.md_theme_light_tertiaryContainer),
    onTertiaryContainer = colorResource(R.color.md_theme_light_onTertiaryContainer),
    error = colorResource(R.color.md_theme_light_error),
    errorContainer = colorResource(R.color.md_theme_light_errorContainer),
    onError = colorResource(R.color.md_theme_light_onError),
    onErrorContainer = colorResource(R.color.md_theme_light_onErrorContainer),
    background = colorResource(R.color.md_theme_light_background),
    onBackground = colorResource(R.color.md_theme_light_onBackground),
    surface = colorResource(R.color.md_theme_light_surface),
    onSurface = colorResource(R.color.md_theme_light_onSurface),
    surfaceVariant = colorResource(R.color.md_theme_light_surfaceVariant),
    onSurfaceVariant = colorResource(R.color.md_theme_light_onSurfaceVariant),
    outline = colorResource(R.color.md_theme_light_outline),
    inverseOnSurface = colorResource(R.color.md_theme_light_inverseOnSurface),
    inverseSurface = colorResource(R.color.md_theme_light_inverseSurface),
    inversePrimary = colorResource(R.color.md_theme_light_primaryInverse),
)

@Composable
private fun dreamDroidDarkColorScheme() = darkColorScheme(
    primary = colorResource(R.color.md_theme_dark_primary),
    onPrimary = colorResource(R.color.md_theme_dark_onPrimary),
    primaryContainer = colorResource(R.color.md_theme_dark_primaryContainer),
    onPrimaryContainer = colorResource(R.color.md_theme_dark_onPrimaryContainer),
    secondary = colorResource(R.color.md_theme_dark_secondary),
    onSecondary = colorResource(R.color.md_theme_dark_onSecondary),
    secondaryContainer = colorResource(R.color.md_theme_dark_secondaryContainer),
    onSecondaryContainer = colorResource(R.color.md_theme_dark_onSecondaryContainer),
    tertiary = colorResource(R.color.md_theme_dark_tertiary),
    onTertiary = colorResource(R.color.md_theme_dark_onTertiary),
    tertiaryContainer = colorResource(R.color.md_theme_dark_tertiaryContainer),
    onTertiaryContainer = colorResource(R.color.md_theme_dark_onTertiaryContainer),
    error = colorResource(R.color.md_theme_dark_error),
    errorContainer = colorResource(R.color.md_theme_dark_errorContainer),
    onError = colorResource(R.color.md_theme_dark_onError),
    onErrorContainer = colorResource(R.color.md_theme_dark_onErrorContainer),
    background = colorResource(R.color.md_theme_dark_background),
    onBackground = colorResource(R.color.md_theme_dark_onBackground),
    surface = colorResource(R.color.md_theme_dark_surface),
    onSurface = colorResource(R.color.md_theme_dark_onSurface),
    surfaceVariant = colorResource(R.color.md_theme_dark_surfaceVariant),
    onSurfaceVariant = colorResource(R.color.md_theme_dark_onSurfaceVariant),
    outline = colorResource(R.color.md_theme_dark_outline),
    inverseOnSurface = colorResource(R.color.md_theme_dark_inverseOnSurface),
    inverseSurface = colorResource(R.color.md_theme_dark_inverseSurface),
    inversePrimary = colorResource(R.color.md_theme_dark_primaryInverse),
)
