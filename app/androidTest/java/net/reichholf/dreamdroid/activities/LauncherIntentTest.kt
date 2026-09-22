package net.reichholf.dreamdroid.activities

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TabbedNavigationActivity] must hand the VIEW payload to the real activity.
 * Android [Intent] constructors are stubs on the JVM, so this stays instrumented.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIntentTest {
    @Test
    fun forwardsViewDataExtrasAndCategories() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Intent(Intent.ACTION_VIEW).apply {
            // setData() then setType() clears the URI. The system sets both together.
            setDataAndType(Uri.parse("dreamdroid://virtual-remote/4"), "text/plain")
            putExtra("query", "news")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val forwarded = forwardedLauncherIntent(context, source, MainActivity::class.java)
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(MainActivity::class.java.name, forwarded.component?.className)
        assertEquals("dreamdroid://virtual-remote/4", forwarded.dataString)
        assertEquals("text/plain", forwarded.type)
        assertEquals("news", forwarded.getStringExtra("query"))
        assertTrue(forwarded.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
    }
}
