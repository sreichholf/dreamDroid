package net.reichholf.dreamdroid.ui.nav

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.DeviceInfoFragment
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Phone shell [NavHost] beachhead. Today only [PhoneNavRoutes.DEVICE_INFO]; other drawer
 * destinations still go through [net.reichholf.dreamdroid.fragment.helper.NavigationHelper].
 */
@Composable
fun PhoneNavHost(
    hostFragment: PhoneNavHostFragment,
    navController: NavHostController = rememberNavController(),
    startDestination: String = PhoneNavRoutes.DEVICE_INFO,
) {
    DisposableEffect(navController) {
        hostFragment.attachNavController(navController)
        onDispose { hostFragment.detachNavController(navController) }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(PhoneNavRoutes.DEVICE_INFO) {
            NestedDeviceInfoDestination(hostFragment = hostFragment)
        }
    }
}

@Composable
private fun NestedDeviceInfoDestination(hostFragment: Fragment) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = R.id.phone_nav_device_info_slot
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { container ->
            ensureNestedDeviceInfo(hostFragment, container.id)
        },
    )
}

/**
 * Mount [DeviceInfoFragment] under the host when missing. Never uses
 * `commitNowAllowingStateLoss`; if the child FM has already saved state, defer via
 * [android.view.View.post] until a safe window (e.g. after rotation restore).
 */
internal fun ensureNestedDeviceInfo(hostFragment: Fragment, containerId: Int) {
    if (!hostFragment.isAdded) return
    val fm = hostFragment.childFragmentManager
    if (fm.findFragmentById(containerId) != null) return
    if (!fm.isStateSaved) {
        commitNestedDeviceInfo(fm, containerId)
        return
    }
    hostFragment.view?.post {
        if (!hostFragment.isAdded) return@post
        val childFm = hostFragment.childFragmentManager
        if (childFm.findFragmentById(containerId) != null || childFm.isStateSaved) return@post
        commitNestedDeviceInfo(childFm, containerId)
    }
}

private fun commitNestedDeviceInfo(fm: FragmentManager, containerId: Int) {
    fm.beginTransaction()
        .replace(containerId, DeviceInfoFragment(), PhoneNavRoutes.DEVICE_INFO)
        .commitNow()
}

fun ComposeView.bindPhoneNavHost(hostFragment: PhoneNavHostFragment) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneNavHost(hostFragment = hostFragment)
        }
    }
}
