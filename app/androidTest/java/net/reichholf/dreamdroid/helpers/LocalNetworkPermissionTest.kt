package net.reichholf.dreamdroid.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNetworkPermissionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun permissionIsAccessLocalNetwork() {
        assertEquals(Manifest.permission.ACCESS_LOCAL_NETWORK, LocalNetworkPermission.PERMISSION)
    }

    @Test
    fun isRequiredMatchesApiLevel37() {
        assertEquals(
            Build.VERSION.SDK_INT >= 37,
            LocalNetworkPermission.isRequired()
        )
    }

    @Test
    fun grantedWithoutThePermissionBelowApi37() {
        assumeTrue(Build.VERSION.SDK_INT < 37)

        assertTrue(LocalNetworkPermission.isGranted(context))
    }

    @Test
    fun grantedMatchesThePlatformCheckFromApi37() {
        assumeTrue(Build.VERSION.SDK_INT >= 37)

        val platformGranted =
            ContextCompat.checkSelfPermission(context, LocalNetworkPermission.PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        assertEquals(platformGranted, LocalNetworkPermission.isGranted(context))
    }
}
