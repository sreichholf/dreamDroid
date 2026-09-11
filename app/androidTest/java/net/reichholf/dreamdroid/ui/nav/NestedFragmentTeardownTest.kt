package net.reichholf.dreamdroid.ui.nav

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testsupport.FragmentHostActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nested NavHost leaves must be removed when their Compose route leaves composition
 * (helper retained for Phase 2.7i chassis cleanup).
 */
@RunWith(AndroidJUnit4::class)
class NestedFragmentTeardownTest {
    class HostFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            return FragmentContainerView(requireContext()).apply {
                id = R.id.detail_title_prio
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        }
    }

    @Test
    fun removeNestedFragmentDetachesLeaf() {
        ActivityScenario.launch(FragmentHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = HostFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, host, "host")
                    .commitNow()
                ensureNestedFragment(host, R.id.detail_title_prio, PhoneNavRoutes.HUB) {
                    Fragment()
                }
                assertNotNull(host.childFragmentManager.findFragmentByTag(PhoneNavRoutes.HUB))
                removeNestedFragment(host, R.id.detail_title_prio, PhoneNavRoutes.HUB)
                assertNull(host.childFragmentManager.findFragmentByTag(PhoneNavRoutes.HUB))
            }
        }
    }
}
