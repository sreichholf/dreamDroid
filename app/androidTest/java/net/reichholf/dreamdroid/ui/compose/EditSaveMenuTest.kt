package net.reichholf.dreamdroid.ui.compose

import android.view.MenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditSaveMenuTest {
    @Test
    fun createMenuHasSaveAndOmitsDelete() {
        val menu = inflate(canDelete = false)
        assertNotNull(menu.findItem(R.id.menu_save))
        assertTrue(menu.findItem(R.id.menu_save).isEnabled)
        assertNull(menu.findItem(R.id.menu_delete))
    }

    @Test
    fun editMenuHasSaveAndDelete() {
        val menu = inflate(canDelete = true)
        assertNotNull(menu.findItem(R.id.menu_save))
        assertNotNull(menu.findItem(R.id.menu_delete))
        assertTrue(menu.findItem(R.id.menu_delete).isEnabled)
    }

    @Test
    fun mutatingDisablesSaveAndDelete() {
        val menu = inflate(canDelete = true, actionsEnabled = false)
        assertFalse(menu.findItem(R.id.menu_save).isEnabled)
        assertFalse(menu.findItem(R.id.menu_delete).isEnabled)
    }

    private fun inflate(canDelete: Boolean, actionsEnabled: Boolean = true): MenuBuilder {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menu = MenuBuilder(context)
        MenuInflater(context).inflateSaveAndDelete(menu, canDelete, actionsEnabled)
        return menu
    }
}
