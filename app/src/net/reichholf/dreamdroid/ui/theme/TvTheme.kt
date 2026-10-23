package net.reichholf.dreamdroid.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Shapes as TvShapes
import androidx.tv.material3.TabColors
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/**
 * Phone [DreamDroidTheme] plus a matching `androidx.tv.material3` scheme so the TV hub
 * uses the same palette, shapes, and night/dynamic preference as phone/tablet.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DreamDroidTvTheme(
    fillBackground: Boolean = true,
    forceDark: Boolean? = null,
    content: @Composable () -> Unit
) {
    DreamDroidTheme(forceDark = forceDark) {
        val phoneScheme = PhoneMaterialTheme.colorScheme
        val phoneShapes = PhoneMaterialTheme.shapes
        val dark = forceDark ?: isDreamDroidDark(LocalContext.current)
        val tvScheme = phoneScheme.toTvColorScheme(dark)
        val tvShapes = TvShapes(
            extraSmall = phoneShapes.extraSmall,
            small = phoneShapes.small,
            medium = phoneShapes.medium,
            large = phoneShapes.large,
            extraLarge = phoneShapes.extraLarge
        )
        TvMaterialTheme(colorScheme = tvScheme, shapes = tvShapes) {
            CompositionLocalProvider(TvLocalContentColor provides tvScheme.onSurface) {
                if (fillBackground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(phoneScheme.background)
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}

/**
 * Hub cards: phone elevated token (not TV Material 3's default white [surface]),
 * with Leanback-style inverse contrast when D-pad focused.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvCardColors(): ClickableSurfaceColors {
    val phone = PhoneMaterialTheme.colorScheme
    return ClickableSurfaceDefaults.colors(
        containerColor = phone.surfaceContainerLow,
        contentColor = phone.onSurface,
        focusedContainerColor = phone.inverseSurface,
        focusedContentColor = phone.inverseOnSurface,
        pressedContainerColor = phone.inverseSurface,
        pressedContentColor = phone.inverseOnSurface
    )
}

/** Round icon buttons beside the hub cards (top bar Settings): the cards' colours. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvIconButtonColors() = PhoneMaterialTheme.colorScheme.let { phone ->
    IconButtonDefaults.colors(
        containerColor = phone.surfaceContainerLow,
        contentColor = phone.onSurface,
        focusedContainerColor = phone.inverseSurface,
        focusedContentColor = phone.inverseOnSurface,
        pressedContainerColor = phone.inverseSurface,
        pressedContentColor = phone.inverseOnSurface
    )
}

/**
 * The pill under the selected top bar tab: light while the tab row has focus (like a focused
 * card), a translucent container tint otherwise.
 */
data class DreamDroidTvPillColors(val active: Color, val inactive: Color)

@Composable
fun dreamDroidTvPillColors(): DreamDroidTvPillColors = PhoneMaterialTheme.colorScheme.let {
    DreamDroidTvPillColors(
        active = it.inverseSurface,
        inactive = it.secondaryContainer.copy(alpha = 0.4f)
    )
}

/** Pill shape of the selected tab, reused by the top bar's other text controls on focus. */
val DreamDroidTvPillShape = RoundedCornerShape(percent = 50)

/**
 * Top bar text controls beside the tabs (profile chip, Recheck): no fill at rest, and on focus
 * the focused tab's light pill ([dreamDroidTvPillColors]), shape and no zoom.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvTopBarItemColors(): ClickableSurfaceColors = PhoneMaterialTheme.colorScheme.let {
    ClickableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = it.onSurface,
        focusedContainerColor = dreamDroidTvPillColors().active,
        focusedContentColor = it.inverseOnSurface,
        pressedContainerColor = dreamDroidTvPillColors().active,
        pressedContentColor = it.inverseOnSurface
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvTopBarItemShape(): ClickableSurfaceShape =
    ClickableSurfaceDefaults.shape(shape = DreamDroidTvPillShape)

@OptIn(ExperimentalTvMaterial3Api::class)
val DreamDroidTvTopBarItemScale: ClickableSurfaceScale = ClickableSurfaceScale.None

/**
 * Top bar tab labels against what lies behind them ([dreamDroidTvPillColors]). While the row
 * has focus the selected tab sits on the light pill, so its label is dark whether or not that
 * tab holds focus; a focused tab off the pill sits on the dark bar. Unselected tabs keep full
 * [onSurfaceVariant] instead of TV's 40% alpha, which falls below text contrast.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvTabColors(rowHasFocus: Boolean): TabColors = PhoneMaterialTheme.colorScheme.let {
    TabDefaults.pillIndicatorTabColors(
        contentColor = it.onSurface,
        inactiveContentColor = it.onSurfaceVariant,
        selectedContentColor = if (rowHasFocus) it.inverseOnSurface else it.onPrimaryContainer,
        focusedContentColor = it.onSurface,
        focusedSelectedContentColor = it.inverseOnSurface
    )
}

/**
 * Drawer labels stay readable when unfocused. TV defaults drop inactive content to 40%
 * alpha, which reads as washed-out gray on the hub.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvDrawerItemColors(): NavigationDrawerItemColors {
    val phone = PhoneMaterialTheme.colorScheme
    return NavigationDrawerItemDefaults.colors(
        contentColor = phone.onSurface,
        inactiveContentColor = phone.onSurfaceVariant,
        selectedContainerColor = phone.secondaryContainer,
        selectedContentColor = phone.onSecondaryContainer
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
internal fun ColorScheme.toTvColorScheme(dark: Boolean): TvColorScheme {
    val seed = if (dark) darkColorScheme() else lightColorScheme()
    return seed.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        border = outline,
        borderVariant = outlineVariant,
        scrim = scrim
    )
}
