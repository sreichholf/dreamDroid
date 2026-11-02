package net.reichholf.dreamdroid.ui.text

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTextTest {
    private val resources: Resources =
        InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun rawTextIsReturnedUnchanged() {
        assertEquals("Receiver said no", UiText.Raw("Receiver said no").asString(resources))
    }

    @Test
    fun resourceWithoutArgsResolvesItsString() {
        assertEquals(
            resources.getString(R.string.save),
            UiText.Resource(R.string.save).asString(resources)
        )
    }

    @Test
    fun resourceWithPlainStringArgIsFormatted() {
        val expected = resources.getString(R.string.autotimer_plugin_failed, "no EPG")
        assertTrue(
            "argument should reach the formatted text: $expected",
            expected.contains("no EPG")
        )
        assertEquals(
            expected,
            UiText.Resource(R.string.autotimer_plugin_failed, listOf("no EPG")).asString(resources)
        )
    }

    @Test
    fun resourceArgThatIsUiTextResolvesBeforeFormatting() {
        val expected = resources.getString(
            R.string.autotimer_plugin_failed,
            resources.getString(R.string.get_content_error)
        )
        assertEquals(
            expected,
            UiText.Resource(
                R.string.autotimer_plugin_failed,
                listOf(UiText.Resource(R.string.get_content_error))
            ).asString(resources)
        )
    }

    @Test
    fun nestedResourceArgsResolveThroughEveryLevel() {
        val inner = UiText.Resource(R.string.autotimer_plugin_failed, listOf("boom"))
        val expected = resources.getString(
            R.string.autotimer_plugin_failed,
            resources.getString(R.string.autotimer_plugin_failed, "boom")
        )
        assertEquals(
            expected,
            UiText.Resource(R.string.autotimer_plugin_failed, listOf(inner)).asString(resources)
        )
    }

    @Test
    fun severalArgsKeepValuesThatAreNotUiText() {
        val expected = resources.getString(R.string.autotimer_more_targets, "Sports", 3)
        assertTrue("count should reach the formatted text: $expected", expected.contains("3"))
        assertEquals(
            expected,
            UiText.Resource(R.string.autotimer_more_targets, listOf("Sports", 3))
                .asString(resources)
        )
    }
}
