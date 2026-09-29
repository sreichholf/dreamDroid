package net.reichholf.dreamdroid.testutil

import net.reichholf.dreamdroid.Profile
import org.junit.rules.ExternalResource

/**
 * Gives the app's [ProfileRepository] an in-memory current profile for one test and puts
 * back what was there before. Tests that reach `requireCurrent()` use this instead of
 * relying on a profile another test left in the app database.
 */
class CurrentProfileRule : ExternalResource() {
    private var previous: Profile? = null

    override fun before() {
        val profiles = dreamDroidApp().profiles
        previous = profiles.current.value
        profiles.setCurrent(
            Profile().apply {
                id = TEST_PROFILE_ID
                name = "Test receiver"
                host = "127.0.0.1"
                port = 80
            }
        )
    }

    override fun after() {
        val profiles = dreamDroidApp().profiles
        val restore = previous
        if (restore == null) {
            profiles.clearCurrent()
        } else {
            profiles.setCurrent(restore)
        }
    }

    private companion object {
        const val TEST_PROFILE_ID = 9_999
    }
}
