package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.window.core.layout.WindowSizeClass
import com.google.android.material.appbar.MaterialToolbar
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerScreen
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

const val SHELL_PROFILE_NAME_TAG = "shell_profile_name"

/**
 * Phone and tablet shell: modal drawer, toolbar, destination rail or bottom chrome,
 * and the shared FAB. Destination chrome and the FAB sit in the content box so the
 * FAB can dodge their measured height. Bar versus rail follows the window size class
 * unless [usesRail] is set.
 */
@Composable
fun PhoneShell(
    drawerListState: DrawerListState,
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    profileName: String,
    connectionLabel: String,
    onProfileClick: () -> Unit,
    onDrawerItemClick: (Int) -> Unit,
    onNavigationClick: () -> Unit,
    destinationController: ShellDestinationBarController,
    fabController: ShellFabController,
    onToolbarReady: (MaterialToolbar) -> Unit,
    modifier: Modifier = Modifier,
    usesRail: Boolean? = null,
    content: @Composable () -> Unit
) {
    val rail = usesRail ?: windowUsesDestinationRail()
    val drawerState = rememberDrawerState(
        if (drawerOpen) DrawerValue.Open else DrawerValue.Closed
    )
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }
            .collect { value ->
                onDrawerOpenChange(value == DrawerValue.Open)
            }
    }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    CompositionLocalProvider(
        LocalShellDestinationBarController provides destinationController,
        LocalShellFabController provides fabController,
        LocalShellUsesDestinationRail provides rail
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            modifier = modifier,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(360.dp)) {
                    DrawerProfileHeader(
                        profileName = profileName,
                        connectionLabel = connectionLabel,
                        onClick = onProfileClick
                    )
                    val status by SessionConnectionHolder.shared.status.collectAsState()
                    DrawerScreen(
                        state = drawerListState,
                        onItemClick = onDrawerItemClick,
                        boxActionsBlocked = status.blocksMutations
                    )
                }
            }
        ) {
            ShellBody(
                usesRail = rail,
                destinationController = destinationController,
                fabController = fabController,
                onNavigationClick = onNavigationClick,
                onToolbarReady = onToolbarReady,
                content = content
            )
        }
    }
}

/**
 * Same bar-versus-rail split as NavigationSuiteScaffoldDefaults: a rail unless the
 * width or height size class is Compact, or the posture is tabletop. The suite
 * scaffold itself is not used; this shell measures the now-playing strip and the
 * destination bar so the FAB can dodge them.
 */
@Composable
private fun windowUsesDestinationRail(): Boolean {
    val info = currentWindowAdaptiveInfoV2()
    val size = info.windowSizeClass
    return !info.windowPosture.isTabletop &&
        size.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        size.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
}

@Composable
private fun ShellBody(
    usesRail: Boolean,
    destinationController: ShellDestinationBarController,
    fabController: ShellFabController,
    onNavigationClick: () -> Unit,
    onToolbarReady: (MaterialToolbar) -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (usesRail) {
            TabletShellDestinationRail(destinationController)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ShellToolbar(
                onNavigationClick = onNavigationClick,
                onToolbarReady = onToolbarReady
            )
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { ShellSnackbarHost() }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(
                            horizontal = if (usesRail) {
                                dimensionResource(R.dimen.content_margin_horizontal)
                            } else {
                                0.dp
                            }
                        )
                ) {
                    content()
                    ShellChromeAndFab(
                        usesRail = usesRail,
                        destinationController = destinationController,
                        fabController = fabController
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ShellChromeAndFab(
    usesRail: Boolean,
    destinationController: ShellDestinationBarController,
    fabController: ShellFabController
) {
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val shown = destinationController.content
    val chromeVisible = if (usesRail) {
        shown is ShellDestinationBarContent.TvMovies && shown.state.nowPlayingStripEnabled
    } else {
        shown !is ShellDestinationBarContent.Hidden
    }
    SideEffect {
        if (!chromeVisible) {
            chromeHeightPx = 0
        }
    }
    val chromeModifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .onGloballyPositioned { coordinates ->
            chromeHeightPx = coordinates.size.height
        }
    if (usesRail) {
        TabletShellNowPlaying(destinationController, chromeModifier)
    } else {
        PhoneShellDestinationChrome(destinationController, chromeModifier)
    }
    ShellFabButton(
        controller = fabController,
        chromeVisible = chromeVisible,
        chromeHeightPx = chromeHeightPx,
        modifier = Modifier.align(Alignment.BottomEnd)
    )
}

@Composable
private fun ShellFabButton(
    controller: ShellFabController,
    chromeVisible: Boolean,
    chromeHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val spec = controller.spec ?: return
    val density = LocalDensity.current
    val gapPx = with(density) {
        dimensionResource(R.dimen.fab_margin_above_chrome).roundToPx()
    }
    val restPx = with(density) {
        dimensionResource(R.dimen.fab_margin_bottom).roundToPx()
    }
    val marginPx = dodgedBottomMarginPx(
        chromeVisible = chromeVisible && chromeHeightPx > 0,
        chromeHeightPx = chromeHeightPx,
        gapPx = gapPx,
        restMarginPx = restPx
    )
    val bottomPad = with(density) { marginPx.toDp() }
    val endPad = dimensionResource(R.dimen.fab_margin_right)
    val label = spec.text
    ExtendedFloatingActionButton(
        onClick = spec.onClick,
        expanded = !label.isNullOrEmpty(),
        icon = {
            Icon(
                painter = painterResource(spec.iconRes),
                contentDescription = null
            )
        },
        text = { Text(label ?: spec.contentDescription) },
        modifier = modifier
            .padding(end = endPad, bottom = bottomPad)
            .alpha(if (spec.lookDisabled) 0.38f else 1f)
            .testTag(SHELL_FAB_TAG)
            .semantics { contentDescription = spec.contentDescription }
    )
}

@Composable
private fun ShellToolbar(onNavigationClick: () -> Unit, onToolbarReady: (MaterialToolbar) -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            val toolbarContext = shellToolbarContext(context)
            MaterialToolbar(toolbarContext).apply {
                id = R.id.toolbar
                val typed = TypedValue()
                val resolved = toolbarContext.theme.resolveAttribute(
                    android.R.attr.actionBarSize,
                    typed,
                    true
                )
                if (resolved) {
                    val px = TypedValue.complexToDimensionPixelSize(
                        typed.data,
                        toolbarContext.resources.displayMetrics
                    )
                    minimumHeight = px
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        px
                    )
                }
                navigationContentDescription = toolbarContext.getString(R.string.drawer_open)
                onToolbarReady(this)
                navigationIcon = DrawerArrowDrawable(toolbarContext)
                setNavigationOnClickListener { onNavigationClick() }
            }
        }
    )
}

/**
 * [MaterialToolbar] requires an AppCompat theme. Compose hosts (and instrumented
 * [androidx.activity.ComponentActivity] tests) do not always provide one, and the
 * old XML toolbar also applied [R.style.ToolbarStyle]'s menu overlay.
 */
private fun shellToolbarContext(context: Context): Context {
    val themed = ContextThemeWrapper(context, R.style.Theme_DreamDroid)
    themed.theme.applyStyle(R.style.ThemeOverlay_DreamDroid_Toolbar, true)
    return themed
}

@Composable
private fun DrawerProfileHeader(profileName: String, connectionLabel: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.navigation_header_height))
    ) {
        Image(
            painter = painterResource(R.drawable.drawer_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = dimensionResource(R.dimen.navigation_header_padding_top))
        ) {
            Image(
                painter = painterResource(R.drawable.dreamdroid_logo_on_dark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentScale = ContentScale.Inside
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = 16.dp,
                        vertical = dimensionResource(R.dimen.list_padding)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(SHELL_PROFILE_NAME_TAG)
                    )
                    Text(
                        text = connectionLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_menu_profiles_light),
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}
