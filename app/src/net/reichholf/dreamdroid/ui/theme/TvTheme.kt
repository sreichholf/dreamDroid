package net.reichholf.dreamdroid.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * True inside [DreamDroidTvTheme]. The shared form chrome in `ui/compose/EditForm.kt`
 * selects the TV variants from `ui/compose/TvForm.kt`: section headers instead of cards,
 * TV rows for switches, pickers and dropdowns, a TV Save button instead of the phone FAB,
 * the TV type scale and 10-foot insets. False everywhere on phone.
 */
val LocalTvFormChrome = staticCompositionLocalOf { false }

/**
 * The margin the surrounding TV chrome already applies to its content, so a screen
 * hosted inside it does not add a second one. `EditFormColumn` in `ui/compose/EditForm.kt`
 * subtracts it; the hub's section pane is the one host that provides it.
 */
data class TvContentInset(val horizontal: Dp = 0.dp, val top: Dp = 0.dp)

val LocalTvContentInset = staticCompositionLocalOf { TvContentInset() }

/**
 * The hub background: a soft primary wash over the background, shared by every TV screen
 * so routes and overlays sit on the same surface as the hub. The wash is composited once,
 * so a screen inside it does not wash the background a second time.
 */
@Composable
fun dreamDroidTvBackgroundBrush(): Brush {
    val background = PhoneMaterialTheme.colorScheme.background
    return Brush.linearGradient(
        colors = listOf(
            PhoneMaterialTheme.colorScheme.primary.copy(alpha = 0.10f).compositeOver(background),
            background
        )
    )
}

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
            CompositionLocalProvider(
                TvLocalContentColor provides tvScheme.onSurface,
                LocalTvFormChrome provides true
            ) {
                if (fillBackground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(dreamDroidTvBackgroundBrush())
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

/** A container colour and the content colour that reads on it. */
data class DreamDroidTvFocusColors(val container: Color, val content: Color)

/**
 * D-pad focus on TV cards, list rows and the Guide cursor: primary, which clears 3:1 against
 * both the resting card fill and the selection tint. The tint itself sits too close to the
 * resting fill to mark focus, and would read as a selected item at rest.
 */
@Composable
fun dreamDroidTvFocusColors(): DreamDroidTvFocusColors = PhoneMaterialTheme.colorScheme.let {
    DreamDroidTvFocusColors(it.primary, it.onPrimary)
}

/**
 * Focus on an item that already rests in the selection colour (the active profile, an enabled
 * timer, the chosen receiver): the inverse fill, so focus reads over the tint and still differs
 * from [dreamDroidTvFocusColors].
 */
@Composable
fun dreamDroidTvActiveFocusColors(): DreamDroidTvFocusColors = PhoneMaterialTheme.colorScheme.let {
    DreamDroidTvFocusColors(it.inverseSurface, it.inverseOnSurface)
}

/**
 * Hub cards: phone elevated token (not TV Material 3's default white [surface]), focused in
 * [dreamDroidTvFocusColors]. [restContainer] replaces the resting fill; one in the selection
 * colour takes [dreamDroidTvActiveFocusColors] on focus.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun dreamDroidTvCardColors(restContainer: Color? = null): ClickableSurfaceColors {
    val phone = PhoneMaterialTheme.colorScheme
    val focus = if (restContainer == null) {
        dreamDroidTvFocusColors()
    } else {
        dreamDroidTvActiveFocusColors()
    }
    return ClickableSurfaceDefaults.colors(
        containerColor = restContainer ?: phone.surfaceContainerLow,
        contentColor = phone.onSurface,
        focusedContainerColor = focus.container,
        focusedContentColor = focus.content,
        pressedContainerColor = focus.container,
        pressedContentColor = focus.content
    )
}

/** Round icon buttons in the hub's top bar (Settings): inverse on focus, like its tabs. */
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
