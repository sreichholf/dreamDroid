package net.reichholf.dreamdroid.ui.nav

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerEpgModePrefsTest {
    @Test
    fun saveListAndMultiRoundTrip() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        DrawerEpgMode.save(prefs, DrawerEpgMode.MULTI)
        assertTrue(DrawerEpgMode.isMulti(prefs))
        DrawerEpgMode.save(prefs, "nope")
        assertFalse(DrawerEpgMode.isMulti(prefs))
        DrawerEpgMode.saveList(ctx)
        assertFalse(DrawerEpgMode.isMulti(prefs))
        DrawerEpgMode.saveMulti(ctx)
        assertTrue(DrawerEpgMode.isMulti(prefs))
        DrawerEpgMode.saveList(ctx)
    }
}
