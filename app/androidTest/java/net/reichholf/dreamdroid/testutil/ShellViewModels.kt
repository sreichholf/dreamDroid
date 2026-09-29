package net.reichholf.dreamdroid.testutil

import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
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
    val clients = EnigmaClientFactory(context, profiles, EnigmaOkHttp())
    val sessions = SessionConnectionHolder()
    return ShellViewModel(
        ReceiverRepository(clients, profiles),
        profiles,
        ReceiverProfileCheckRepository(context, profiles, clients),
        ServiceRepository(context, clients, profiles, AppDatabase.inMemory(context), sessions),
        sessions,
        SettingsRepository(context.getSharedPreferences("shell-test", 0))
    )
}
