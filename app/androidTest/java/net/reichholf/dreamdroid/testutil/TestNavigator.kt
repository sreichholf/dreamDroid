package net.reichholf.dreamdroid.testutil

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.PhoneNavigator
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * A [PhoneNavigator] over fresh nav state, for a destination hosted without MainActivity.
 * It has no drawer; its lifecycle stays resumed. The settings are a test preferences file.
 */
fun testNavigator(
    profiles: ProfileRepository,
    sessions: SessionConnectionHolder = SessionConnectionHolder()
): PhoneNavigator {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return PhoneNavigator(
        state = PhoneNavHostState(SavedStateHandle()),
        lifecycleOwner = ResumedLifecycleOwner(),
        highlighter = null,
        profiles = profiles,
        settings = SettingsRepository(context.getSharedPreferences("navigator-test", 0)),
        sessions = sessions
    )
}

private class ResumedLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
}
