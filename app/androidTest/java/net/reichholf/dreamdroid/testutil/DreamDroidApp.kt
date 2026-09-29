package net.reichholf.dreamdroid.testutil

import androidx.test.core.app.ApplicationProvider
import net.reichholf.dreamdroid.DreamDroid

/**
 * The running app. Tests that drive a real activity reach the app's Hilt singletons
 * through the fields Hilt injected into it.
 */
fun dreamDroidApp(): DreamDroid = ApplicationProvider.getApplicationContext()
