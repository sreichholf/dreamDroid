package net.reichholf.dreamdroid.appwidget

import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.TestProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [VirtualRemoteWidgetConfiguration.getWidgetProfile] reads the widget's profile from the repository. */
class WidgetProfileTest {
    private val profiles = TestProfiles()
    private val context = profiles.context
    private val repository = profiles.repository

    @Test
    fun configuredWidgetGetsItsProfile() {
        save("other")
        val box = save("box")
        configure(WIDGET_ID, box.id!!)

        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(
            context,
            repository,
            WIDGET_ID
        )

        assertEquals("box", profile?.name)
        assertEquals(box.id, profile?.id)
    }

    @Test
    fun unconfiguredWidgetHasNoProfile() {
        save("box")

        assertNull(
            VirtualRemoteWidgetConfiguration.getWidgetProfile(context, repository, WIDGET_ID)
        )
    }

    @Test
    fun deletedProfileLeavesTheWidgetWithoutOne() {
        val box = save("box")
        configure(WIDGET_ID, box.id!!)
        repository.delete(box)

        assertNull(
            VirtualRemoteWidgetConfiguration.getWidgetProfile(context, repository, WIDGET_ID)
        )
    }

    private fun save(name: String): Profile = Profile().apply {
        this.name = name
        host = "$name.local"
    }.also(repository::save)

    private fun configure(widgetId: Int, profileId: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(VirtualRemoteWidgetConfiguration.getProfileIdKey(widgetId), profileId)
            .commit()
    }

    private companion object {
        const val WIDGET_ID = 7
    }
}
