package net.reichholf.dreamdroid.testutil

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * A [ShellViewModel] over [profiles], an in-memory database, and a fresh session. The
 * settings are the app's default preferences. Its AutoTimer scope ends when it is cleared.
 */
fun testShellViewModel(profiles: ProfileRepository): ShellViewModel {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val capabilities = WebIfCapabilitiesRepository()
    val clients = ReceiverApiFactory(context, profiles, EnigmaOkHttp(), capabilities)
    val sessions = SessionConnectionHolder()
    val scope = MainScope()
    return ShellViewModel(
        ReceiverRepository(clients, profiles),
        AutoTimerRepository(clients, profiles, scope),
        profiles,
        ReceiverProfileCheckRepository(profiles, clients, capabilities),
        ServiceRepository(context, clients, profiles, AppDatabase.inMemory(context), sessions),
        sessions,
        SettingsRepository(context.getSharedPreferences("shell-test", 0)),
        capabilities
    ).apply { addCloseable { scope.cancel() } }
}
