package net.reichholf.dreamdroid.widget

import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DualpaneFabLayoutTest {
    @Test
    fun mainAndReloadFabsUseGravityNotDetailAnchor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dualpane, null, false)
        val fabMain = root.findViewById<FloatingActionButton>(R.id.fab_main)
        val fabReload = root.findViewById<FloatingActionButton>(R.id.fab_reload)
        val mainLp = fabMain.layoutParams as CoordinatorLayout.LayoutParams
        val reloadLp = fabReload.layoutParams as CoordinatorLayout.LayoutParams

        assertEquals(View.NO_ID, mainLp.anchorId)
        assertTrue((mainLp.gravity and Gravity.BOTTOM) == Gravity.BOTTOM)
        assertTrue((mainLp.gravity and Gravity.END) == Gravity.END)

        assertEquals(View.NO_ID, reloadLp.anchorId)
        assertTrue((reloadLp.gravity and Gravity.TOP) == Gravity.TOP)
        assertTrue((reloadLp.gravity and Gravity.END) == Gravity.END)
    }
}
