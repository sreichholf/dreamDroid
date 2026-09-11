package net.reichholf.dreamdroid.tv.activities

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.Metadata
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvMainActivityKotlinTest {
    @Test
    fun mainActivityIsKotlin() {
        assertNotNull(MainActivity::class.java.getAnnotation(Metadata::class.java))
    }
}
