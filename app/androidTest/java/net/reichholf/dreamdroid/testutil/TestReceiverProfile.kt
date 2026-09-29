package net.reichholf.dreamdroid.testutil

import net.reichholf.dreamdroid.Profile

/**
 * An in-memory current profile for a `@HiltAndroidTest` whose screen reaches
 * `requireCurrent()`. Nothing listens at its address. Each test gets a fresh graph, so
 * nothing has to be put back afterwards.
 */
fun testReceiverProfile(): Profile = Profile().apply {
    id = 9_999
    name = "Test receiver"
    host = "127.0.0.1"
    port = 80
}
