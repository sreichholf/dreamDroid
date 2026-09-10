package net.reichholf.dreamdroid.ui.drawer

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

data class DrawerMenuItem(
    val id: Int,
    @StringRes val titleRes: Int,
    @AttrRes val iconAttr: Int,
)

data class DrawerSection(
    @StringRes val titleRes: Int,
    val items: List<DrawerMenuItem>,
)

object DrawerDestinations {
    val boxActions: List<DrawerMenuItem> = listOf(
        DrawerMenuItem(R.id.menu_navigation_power, R.string.powercontrol, R.attr.ic_menu_power_off),
        DrawerMenuItem(R.id.menu_navigation_sleeptimer, R.string.sleeptimer, R.attr.ic_menu_sleeptimer),
        DrawerMenuItem(R.id.menu_navigation_message, R.string.send_message, R.attr.ic_menu_mail),
    )

    val sections: List<DrawerSection> = listOf(
        DrawerSection(
            titleRes = R.string.control,
            items = listOf(
                DrawerMenuItem(R.id.menu_navigation_services, R.string.live_movie, R.attr.ic_menu_services),
                DrawerMenuItem(R.id.menu_navigation_epg, R.string.epg, R.attr.ic_menu_epg),
                DrawerMenuItem(R.id.menu_navigation_remote, R.string.virtual_remote, R.attr.ic_menu_remote),
                DrawerMenuItem(R.id.menu_navigation_current, R.string.current_event, R.attr.ic_menu_current),
                DrawerMenuItem(R.id.menu_navigation_zap, R.string.zap, R.attr.ic_menu_zap),
            ),
        ),
        DrawerSection(
            titleRes = R.string.tools,
            items = listOf(
                DrawerMenuItem(R.id.menu_navigation_screenshot, R.string.screenshot, R.attr.ic_menu_picture),
                DrawerMenuItem(R.id.menu_navigation_device_info, R.string.device_info, R.attr.ic_menu_device),
                DrawerMenuItem(R.id.menu_navigation_signal, R.string.signal_meter, R.attr.ic_menu_signal),
            ),
        ),
    )

    val settings: DrawerMenuItem = DrawerMenuItem(
        R.id.menu_navigation_settings,
        R.string.settings,
        R.attr.ic_menu_settings,
    )
}

class DrawerListState {
    var selectedItemId by mutableIntStateOf(R.id.menu_none)
        private set

    fun select(itemId: Int) {
        selectedItemId = itemId
    }

    fun clearSelection() {
        selectedItemId = R.id.menu_none
    }
}

@Composable
private fun resolveThemeDrawable(@AttrRes attr: Int): Int {
    val context = LocalContext.current
    val typed = TypedValue()
    context.theme.resolveAttribute(attr, typed, true)
    return typed.resourceId
}

@Composable
private fun DrawerBoxActions(
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DrawerDestinations.boxActions.forEach { item ->
            val iconRes = resolveThemeDrawable(item.iconAttr)
            val label = stringResource(item.titleRes)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.Button }
                    .clickable { onItemClick(item.id) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                if (iconRes != 0) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerDestinationItem(
    item: DrawerMenuItem,
    selected: Boolean,
    onItemClick: (Int) -> Unit,
) {
    val iconRes = resolveThemeDrawable(item.iconAttr)
    NavigationDrawerItem(
        label = { Text(stringResource(item.titleRes)) },
        selected = selected,
        onClick = { onItemClick(item.id) },
        icon = {
            if (iconRes != 0) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                )
            }
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun DrawerScreen(
    state: DrawerListState,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DrawerBoxActions(onItemClick = onItemClick)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            DrawerDestinations.sections.forEach { section ->
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                section.items.forEach { item ->
                    DrawerDestinationItem(
                        item = item,
                        selected = state.selectedItemId == item.id,
                        onItemClick = onItemClick,
                    )
                }
            }
        }
        HorizontalDivider()
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            DrawerDestinationItem(
                item = DrawerDestinations.settings,
                selected = state.selectedItemId == DrawerDestinations.settings.id,
                onItemClick = onItemClick,
            )
        }
    }
}

fun ComposeView.bindDrawerScreen(
    state: DrawerListState,
    onItemClick: (Int) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DrawerScreen(state = state, onItemClick = onItemClick)
        }
    }
}
