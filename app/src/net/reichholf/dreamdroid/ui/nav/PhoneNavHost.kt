package net.reichholf.dreamdroid.ui.nav

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.DeviceInfoFragment
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Phone shell [NavHost] beachhead. Today only [PhoneNavRoutes.DEVICE_INFO]; other drawer
 * destinations still go through [net.reichholf.dreamdroid.fragment.helper.NavigationHelper].
 */
@Composable
fun PhoneNavHost(
    hostFragment: Fragment,
    navController: NavHostController = rememberNavController(),
    startDestination: String = PhoneNavRoutes.DEVICE_INFO,
) {
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
            val fm = hostFragment.childFragmentManager
            if (fm.findFragmentById(container.id) == null) {
                fm.beginTransaction()
                    .replace(container.id, DeviceInfoFragment(), PhoneNavRoutes.DEVICE_INFO)
                    .commitNowAllowingStateLoss()
            }
        },
    )
}

fun ComposeView.bindPhoneNavHost(hostFragment: Fragment) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneNavHost(hostFragment = hostFragment)
        }
    }
}
