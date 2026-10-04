package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.core.layout.WindowSizeClass
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerScreen

const val SHELL_PROFILE_NAME_TAG = "shell_profile_name"

/**
 * Phone and tablet shell: modal drawer, top app bar, destination rail or bottom chrome,
 * and the shared FAB. The top bar and the bottom chrome slide away while content scrolls
 * down ([ShellChromeScrollState]). Bar versus rail follows the window size class unless
 * [usesRail] is set.
 */
@Composable
fun PhoneShell(
    drawerListState: DrawerListState,
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    profileName: String,
    connectionLabel: String,
    boxActionsBlocked: Boolean,
    onProfileClick: () -> Unit,
    onDrawerItemClick: (Int) -> Unit,
    onNavigationClick: () -> Unit,
    destinationController: ShellDestinationBarController,
    fabController: ShellFabController,
    topBarController: ShellTopBarController,
    modifier: Modifier = Modifier,
    trailingTopBarActions: List<ShellTopBarAction> = emptyList(),
    autoTimerInDrawer: Boolean = false,
    sleepTimerInDrawer: Boolean = true,
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
    val snackbarHostState = remember { SnackbarHostState() }
    val chromeScroll = remember { ShellChromeScrollState() }
    CompositionLocalProvider(
        LocalShellSnackbarHostState provides snackbarHostState,
        LocalShellDestinationBarController provides destinationController,
        LocalShellFabController provides fabController,
        LocalShellTopBarController provides topBarController,
        LocalShellChromeScrollState provides chromeScroll
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
                    DrawerScreen(
                        state = drawerListState,
                        onItemClick = onDrawerItemClick,
                        boxActionsBlocked = boxActionsBlocked,
                        autoTimerAvailable = autoTimerInDrawer,
                        sleepTimerAvailable = sleepTimerInDrawer
                    )
                }
            }
        ) {
            ShellBody(
                usesRail = rail,
                destinationController = destinationController,
                fabController = fabController,
                onNavigationClick = onNavigationClick,
                topBarController = topBarController,
                trailingTopBarActions = trailingTopBarActions,
                snackbarHostState = snackbarHostState,
                chromeScroll = chromeScroll,
                content = content
            )
        }
    }
}

/**
 * Same bar-versus-rail split as NavigationSuiteScaffoldDefaults: a rail unless the
 * width or height size class is Compact, or the posture is tabletop. The suite
 * scaffold itself is not used: it cannot hide the bar and the now-playing strip on scroll.
 */
@Composable
private fun windowUsesDestinationRail(): Boolean {
    val info = currentWindowAdaptiveInfoV2()
    val size = info.windowSizeClass
    return !info.windowPosture.isTabletop &&
        size.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        size.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellBody(
    usesRail: Boolean,
    destinationController: ShellDestinationBarController,
    fabController: ShellFabController,
    onNavigationClick: () -> Unit,
    topBarController: ShellTopBarController,
    trailingTopBarActions: List<ShellTopBarAction>,
    snackbarHostState: SnackbarHostState,
    chromeScroll: ShellChromeScrollState,
    content: @Composable () -> Unit
) {
    val hideOnScroll = !rememberTouchExplorationEnabled()
    val currentHideOnScroll by rememberUpdatedState(hideOnScroll)
    LaunchedEffect(hideOnScroll) {
        if (!hideOnScroll) {
            chromeScroll.revealAll()
        }
    }
    // Coming back to the app brings back the bottom chrome; the top bar waits for a scroll up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, chromeScroll) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                chromeScroll.revealBottom()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A bar the destination replaced cannot hide: it would swallow scrolling unseen.
    val canHideTopBar = remember(topBarController) {
        {
            currentHideOnScroll && !topBarController.replaced && !topBarController.keepInView
        }
    }
    val canHideBottomChrome = remember { { currentHideOnScroll } }
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = chromeScroll.topBar,
        canScroll = canHideTopBar
    )
    val bottomChromeScroll = BottomAppBarDefaults.exitAlwaysScrollBehavior(
        state = chromeScroll.bottomBar,
        canScroll = canHideBottomChrome
    )
    // The FAB shows its label only while the top bar is fully in view.
    val fabExpanded by remember(chromeScroll) {
        derivedStateOf { chromeScroll.topBar.collapsedFraction == 0f }
    }
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
                .nestedScroll(bottomChromeScroll.nestedScrollConnection)
                .nestedScroll(topBarScroll.nestedScrollConnection)
        ) {
            if (!topBarController.replaced) {
                ShellTopAppBar(
                    controller = topBarController,
                    onNavigationClick = onNavigationClick,
                    trailingActions = trailingTopBarActions,
                    scrollBehavior = topBarScroll
                )
            }
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                    ShellFabButton(
                        controller = fabController,
                        expanded = fabExpanded,
                        aboveChrome = destinationController.content.showsBottomChrome(usesRail),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            val chromeModifier = Modifier
                .fillMaxWidth()
                .collapsingBottomChrome(chromeScroll)
            if (usesRail) {
                TabletShellNowPlaying(destinationController, chromeModifier)
            } else {
                PhoneShellDestinationChrome(destinationController, chromeModifier)
            }
        }
    }
}

/** Whether a hub shows chrome under the content: the bar on phone, the strip on tablet. */
private fun ShellDestinationBarContent.showsBottomChrome(usesRail: Boolean): Boolean =
    if (usesRail) {
        this is ShellDestinationBarContent.TvMovies && state.nowPlayingStripEnabled
    } else {
        this !is ShellDestinationBarContent.Hidden
    }

@Composable
private fun ShellFabButton(
    controller: ShellFabController,
    expanded: Boolean,
    aboveChrome: Boolean,
    modifier: Modifier = Modifier
) {
    val spec = controller.spec ?: return
    val bottomPad = dimensionResource(
        if (aboveChrome) R.dimen.fab_margin_above_chrome else R.dimen.fab_margin_bottom
    )
    val endPad = dimensionResource(R.dimen.fab_margin_right)
    val label = spec.text
    ExtendedFloatingActionButton(
        onClick = spec.onClick,
        expanded = expanded && !label.isNullOrEmpty(),
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
