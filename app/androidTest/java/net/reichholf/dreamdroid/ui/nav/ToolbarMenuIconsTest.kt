package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.MenuInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarMenuIconsTest {
    @Test
    fun dayThemeActionIconsPaintOnSurfaceAfterTint() {
        assertActionMenusPaintOnSurface(night = false)
    }

    @Test
    fun nightThemeActionIconsPaintOnSurfaceAfterTint() {
        assertActionMenusPaintOnSurface(night = true)
    }

    @Test
    fun defaultBouquetSetIconHonorsThemeWithoutHelper() {
        assertDefaultBouquetSetIcon(night = false, runHelper = false)
        assertDefaultBouquetSetIcon(night = true, runHelper = false)
    }

    @Test
    fun defaultBouquetSetIconStillPaintsAfterPrepareTint() {
        assertDefaultBouquetSetIcon(night = false, runHelper = true)
        assertDefaultBouquetSetIcon(night = true, runHelper = true)
    }

    private fun assertActionMenusPaintOnSurface(night: Boolean) {
        val themed = themedContext(night)
        val toolbar = Toolbar(themed)
        val inflater = MenuInflater(themed)
        val menus = intArrayOf(
            R.menu.search,
            R.menu.servicelistpage,
            R.menu.timerlist,
            R.menu.locactions_and_tags,
            R.menu.profiles,
            R.menu.epgbouquet,
            R.menu.edit_delete
        )
        var menuIndex = 0
        while (menuIndex < menus.size) {
            val popup = PopupMenu(themed, toolbar)
            inflater.inflate(menus[menuIndex], popup.menu)
            tintToolbarMenuIcons(toolbar, popup.menu)
            var itemIndex = 0
            while (itemIndex < popup.menu.size()) {
                val item = popup.menu.getItem(itemIndex)
                val icon = item.icon
                if (icon != null) {
                    assertPaintsOnSurface(
                        icon,
                        themed,
                        "night=$night menu=${menus[menuIndex]} item=${item.title}"
                    )
                }
                itemIndex++
            }
            menuIndex++
        }
    }

    private fun assertDefaultBouquetSetIcon(night: Boolean, runHelper: Boolean) {
        val themed = themedContext(night)
        val toolbar = Toolbar(themed)
        val popup = PopupMenu(themed, toolbar)
        MenuInflater(themed).inflate(R.menu.servicelistpage, popup.menu)
        val item = popup.menu.findItem(R.id.menu_default)
        val swaps = intArrayOf(R.drawable.ic_action_nofav, R.drawable.ic_action_fav)
        var i = 0
        while (i < swaps.size) {
            item.setIcon(swaps[i])
            if (runHelper) {
                tintToolbarMenuIcons(toolbar, popup.menu)
            }
            assertPaintsOnSurface(
                item.icon!!,
                themed,
                "night=$night helper=$runHelper icon=${swaps[i]}"
            )
            i++
        }
        val loaded = AppCompatResources.getDrawable(themed, R.drawable.ic_action_nofav)
        assertPaintsOnSurface(loaded!!, themed, "night=$night loaded nofav")
    }

    private fun themedContext(night: Boolean): Context {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(app.resources.configuration)
        val nightBits = if (night) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        val configContext = app.createConfigurationContext(config)
        val themeRes = if (night) {
            R.style.Theme_DreamDroid_Night
        } else {
            R.style.Theme_DreamDroid
        }
        return ContextThemeWrapper(configContext, themeRes)
    }

    private fun assertPaintsOnSurface(icon: Drawable, themed: Context, label: String) {
        val toolbar = Toolbar(themed)
        val onSurface = MaterialColors.getColor(toolbar, MaterialR.attr.colorOnSurface)
        val surface = MaterialColors.getColor(toolbar, MaterialR.attr.colorSurface)
        val hits = onSurfaceHits(icon, surface, onSurface)
        assertTrue(
            "toolbar icon must paint onSurface ($label hits=$hits " +
                "onSurface=#${Integer.toHexString(onSurface)} " +
                "surface=#${Integer.toHexString(surface)})",
            hits > 10
        )
    }

    private fun onSurfaceHits(icon: Drawable, surface: Int, onSurface: Int): Int {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(surface)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
        var hits = 0
        var y = 0
        while (y < size) {
            var x = 0
            while (x < size) {
                if (rgbDistance(bitmap.getPixel(x, y), onSurface) < 40) {
                    hits++
                }
                x++
            }
            y++
        }
        return hits
    }

    private fun rgbDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xff
        val ag = (a shr 8) and 0xff
        val ab = a and 0xff
        val br = (b shr 16) and 0xff
        val bg = (b shr 8) and 0xff
        val bb = b and 0xff
        return abs(ar - br) + abs(ag - bg) + abs(ab - bb)
    }
}
