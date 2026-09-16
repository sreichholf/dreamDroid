package net.reichholf.dreamdroid.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

private val DreamDroidShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DreamDroidTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = forceDark ?: isDreamDroidDark(context)
    val scheme = if (Build.VERSION.SDK_INT >= 31 && usesDynamicThemeColors(context)) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        dreamDroidDarkColorScheme()
    } else {
        dreamDroidLightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, shapes = DreamDroidShapes) {
        // Dialog-hosted ComposeView inherits View contentColor (black in night). Override so
        // Text() without an explicit color uses the DreamDroid scheme, not the XML dialog.
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

fun isDreamDroidDark(context: Context): Boolean = when (DreamDroid.getThemeType(context)) {
    0 -> false

    1 -> true

    else -> {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        night == Configuration.UI_MODE_NIGHT_YES
    }
}

fun usesDynamicThemeColors(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) {
        return false
    }
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, false)
}

// surfaceContainer* / outlineVariant must be set. lightColorScheme/darkColorScheme
// otherwise fill baseline Material purple neutrals, which stock DatePicker,
// TimePicker, and AlertDialog use when colors= is omitted.
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
    outlineVariant = colorResource(R.color.md_theme_light_outlineVariant),
    scrim = colorResource(R.color.md_theme_light_scrim),
    surfaceTint = colorResource(R.color.md_theme_light_primary),
    surfaceBright = colorResource(R.color.md_theme_light_surfaceBright),
    surfaceDim = colorResource(R.color.md_theme_light_surfaceDim),
    surfaceContainer = colorResource(R.color.md_theme_light_surfaceContainer),
    surfaceContainerHigh = colorResource(R.color.md_theme_light_surfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.md_theme_light_surfaceContainerHighest),
    surfaceContainerLow = colorResource(R.color.md_theme_light_surfaceContainerLow),
    surfaceContainerLowest = colorResource(R.color.md_theme_light_surfaceContainerLowest),
    inverseOnSurface = colorResource(R.color.md_theme_light_inverseOnSurface),
    inverseSurface = colorResource(R.color.md_theme_light_inverseSurface),
    inversePrimary = colorResource(R.color.md_theme_light_primaryInverse)
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
    outlineVariant = colorResource(R.color.md_theme_dark_outlineVariant),
    scrim = colorResource(R.color.md_theme_dark_scrim),
    surfaceTint = colorResource(R.color.md_theme_dark_primary),
    surfaceBright = colorResource(R.color.md_theme_dark_surfaceBright),
    surfaceDim = colorResource(R.color.md_theme_dark_surfaceDim),
    surfaceContainer = colorResource(R.color.md_theme_dark_surfaceContainer),
    surfaceContainerHigh = colorResource(R.color.md_theme_dark_surfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.md_theme_dark_surfaceContainerHighest),
    surfaceContainerLow = colorResource(R.color.md_theme_dark_surfaceContainerLow),
    surfaceContainerLowest = colorResource(R.color.md_theme_dark_surfaceContainerLowest),
    inverseOnSurface = colorResource(R.color.md_theme_dark_inverseOnSurface),
    inverseSurface = colorResource(R.color.md_theme_dark_inverseSurface),
    inversePrimary = colorResource(R.color.md_theme_dark_primaryInverse)
)
