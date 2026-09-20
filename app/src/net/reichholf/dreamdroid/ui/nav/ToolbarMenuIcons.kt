package net.reichholf.dreamdroid.ui.nav

import android.content.res.ColorStateList
import android.view.Menu
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

/**
 * Action-bar vectors are white (`#FFFFFF`). MenuProvider inflation uses the
 * Activity context, so `Widget.Material3.Toolbar.OnSurface`'s overlay does not
 * tint them and they vanish on a light app bar (same class of bug as the EPG
 * bouquet picker). Re-tint from the toolbar's `colorOnSurface`.
 */
fun tintToolbarMenuIcons(toolbar: Toolbar?, menu: Menu) {
    if (toolbar == null) {
        return
    }
    val color = MaterialColors.getColor(toolbar, MaterialR.attr.colorOnSurface)
    val tint = ColorStateList.valueOf(color)
    var i = 0
    while (i < menu.size()) {
        val icon = menu.getItem(i).icon
        if (icon != null) {
            val wrapped = DrawableCompat.wrap(icon.mutate())
            DrawableCompat.setTintList(wrapped, tint)
            menu.getItem(i).icon = wrapped
        }
        i++
    }
}
