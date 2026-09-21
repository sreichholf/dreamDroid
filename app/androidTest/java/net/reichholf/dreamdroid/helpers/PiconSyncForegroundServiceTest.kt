package net.reichholf.dreamdroid.helpers

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the Android 14+ crash where [PiconSyncWorker] promotes WorkManager's
 * SystemForegroundService with [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
 * while the merged manifest lacked that type.
 */
@RunWith(AndroidJUnit4::class)
class PiconSyncForegroundServiceTest {
    @Test
    fun systemForegroundServiceDeclaresDataSyncType() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(
            context.packageName,
            "androidx.work.impl.foreground.SystemForegroundService"
        )
        val info = context.packageManager.getServiceInfo(
            component,
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
        val types = info.foregroundServiceType
        assertTrue(
            "Picon sync setForeground(dataSync) needs this type on " +
                "SystemForegroundService (targetSdk 34+ crash otherwise); " +
                "got types=$types",
            (types and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) != 0
        )
    }
}
