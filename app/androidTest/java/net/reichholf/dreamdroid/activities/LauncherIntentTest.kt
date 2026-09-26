package net.reichholf.dreamdroid.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launcher and `dreamdroid://` filters live on the real activities.
 * Android [Intent] resolution is a stub on the JVM, so this stays instrumented.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIntentTest {
    @Test
    fun phoneLauncherResolvesToPhoneMainActivity() {
        assertEquals(
            PHONE_MAIN,
            resolve(Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER)
        )
    }

    @Test
    fun leanbackLauncherResolvesToTvMainActivity() {
        assertEquals(
            TV_MAIN,
            resolve(Intent.ACTION_MAIN, Intent.CATEGORY_LEANBACK_LAUNCHER)
        )
    }

    @Test
    fun dreamdroidViewResolvesToPhoneMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("dreamdroid://virtual-remote/4")).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        assertEquals(PHONE_MAIN, resolved?.activityInfo?.name)
    }

    @Test
    fun removedActivitiesAreAbsent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES
        )
        val names = info.activities.orEmpty().map { it.name }.toSet()
        assertFalse(names.any { it.endsWith("TabbedNavigationActivity") })
        assertFalse(names.any { it.endsWith("MultiEpgActivity") })
        assertFalse(names.any { it.endsWith("PreferenceActivity") })
    }

    private fun resolve(action: String, category: String): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(action).apply {
            addCategory(category)
            setPackage(context.packageName)
        }
        return context.packageManager
            .resolveActivity(intent, 0)
            ?.activityInfo
            ?.name
    }

    private companion object {
        const val PHONE_MAIN = "net.reichholf.dreamdroid.activities.MainActivity"
        const val TV_MAIN = "net.reichholf.dreamdroid.tv.activities.MainActivity"
    }
}
