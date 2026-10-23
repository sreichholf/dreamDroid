package net.reichholf.dreamdroid.testutil

import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.data.LastLiveStreamAutoStart
import net.reichholf.dreamdroid.data.LastLiveStreamRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
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
 * settings are the app's default preferences.
 */
fun testShellViewModel(profiles: ProfileRepository): ShellViewModel {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val capabilities = WebIfCapabilitiesRepository()
    val clients = ReceiverApiFactory(context, profiles, EnigmaOkHttp(), capabilities)
    val sessions = SessionConnectionHolder()
    val preferences = context.getSharedPreferences("shell-test", 0)
    val settings = SettingsRepository(preferences)
    return ShellViewModel(
        ReceiverRepository(clients, profiles),
        ReceiverPluginsRepository(clients, profiles),
        profiles,
        ReceiverProfileCheckRepository(profiles, clients, capabilities),
        ServiceRepository(
            context,
            clients,
            profiles,
            AppDatabase.inMemory(context),
            sessions,
            ReceiverRepository(clients, profiles)
        ),
        sessions,
        settings,
        LastLiveStreamAutoStart(
            settings,
            LastLiveStreamRepository(preferences, profiles),
            ReceiverRepository(clients, profiles),
            sessions
        ),
        capabilities
    )
}
