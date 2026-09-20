package net.reichholf.dreamdroid.ui.nav

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.MenuInflater
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

    private fun assertActionMenusPaintOnSurface(night: Boolean) {
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
        val themed = ContextThemeWrapper(configContext, themeRes)
        val toolbar = Toolbar(themed)
        val onSurface = MaterialColors.getColor(toolbar, MaterialR.attr.colorOnSurface)
        val surface = MaterialColors.getColor(toolbar, MaterialR.attr.colorSurface)
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
                    val hits = onSurfaceHits(icon, surface, onSurface)
                    assertTrue(
                        "toolbar icon must paint onSurface " +
                            "(night=$night menu=${menus[menuIndex]} " +
                            "item=${item.title} hits=$hits " +
                            "onSurface=#${Integer.toHexString(onSurface)} " +
                            "surface=#${Integer.toHexString(surface)})",
                        hits > 10
                    )
                }
                itemIndex++
            }
            menuIndex++
        }
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
