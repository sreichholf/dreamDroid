package net.reichholf.dreamdroid.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ReceiverPlugins
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReceiverPluginsRepositoryTest {
    private val receiver = TestReceiver()
    private val repository =
        ReceiverPluginsRepository(receiverApis(receiver.repository), receiver.repository)

    @BeforeEach
    fun setUp() {
        receiver.start()
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))
    }

    @AfterEach
    fun tearDown() {
        receiver.shutdown()
    }

    @Test
    fun oneRequestAnswersForEveryPlugin() = runBlocking<Unit> {
        assertNull(repository.known())
        assertEquals(PluginPresence.Unknown, repository.autoTimerPresence.first())

        val answer = repository.refresh().value

        val expected = ReceiverPlugins(AutoTimerPlugin.Installed(AutoTimerApi.V1_6), vps = false)
        assertEquals(expected, answer)
        assertEquals(expected, repository.known())
        assertEquals(PluginPresence.Present, repository.autoTimerPresence.first())
        assertEquals(1, receiver.requestsTo(EXTERNALS).size)
    }

    @Test
    fun theVpsPluginIsPresentWhenListed() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, externals("vpsplugin"))

        repository.refresh()

        assertEquals(ReceiverPlugins(AutoTimerPlugin.Missing, vps = true), repository.known())
        assertEquals(PluginPresence.Absent, repository.autoTimerPresence.first())
    }

    @Test
    fun aFailedRequestKeepsTheLastAnswer() = runBlocking<Unit> {
        repository.refresh()
        receiver.fail(EXTERNALS)

        val failed = repository.refresh()

        assertNull(failed.value)
        assertEquals(500, (failed.error?.failure as? EnigmaFailure.Http)?.code)
        assertEquals(PluginPresence.Present, repository.autoTimerPresence.first())
    }

    @Test
    fun aFailedFirstRequestStaysUnknown() = runBlocking<Unit> {
        receiver.fail(EXTERNALS)

        repository.refresh()

        assertNull(repository.known())
    }

    @Test
    fun anUninstalledPluginIsAbsentAfterTheNextRefresh() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, externals("autotimer", "vpsplugin"))
        repository.refresh()

        receiver.respond(EXTERNALS, externals("autotimer"))
        repository.refresh()

        assertEquals(false, repository.known()?.vps)
        assertEquals(PluginPresence.Present, repository.autoTimerPresence.first())
    }

    @Test
    fun aReinstalledPluginIsPresentAgainAfterTheNextRefresh() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, externals("vpsplugin"))
        repository.refresh()
        repository.markVpsAbsent(receiver.repository.requireCurrent())

        repository.refresh()

        assertEquals(true, repository.known()?.vps)
    }

    @Test
    fun markVpsAbsentChangesOnlyTheVpsPlugin() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, externals("autotimer", "vpsplugin"))
        repository.refresh()

        repository.markVpsAbsent(receiver.repository.requireCurrent())

        assertEquals(
            ReceiverPlugins(AutoTimerPlugin.Installed(AutoTimerApi.V1_6), vps = false),
            repository.known()
        )
    }

    @Test
    fun presenceBelongsToTheProfile() = runBlocking<Unit> {
        repository.refresh()
        val other = receiver.repository.requireCurrent()
        receiver.repository.setCurrent(
            Profile().apply {
                id = OTHER_PROFILE_ID
                name = "other"
                host = other.host
                port = other.port
            }
        )

        withTimeout(TIMEOUT) {
            repository.autoTimerPresence.first { it == PluginPresence.Unknown }
        }
        assertNull(repository.known())
    }

    private fun externals(vararg paths: String): String =
        "<e2webifexternals>" + paths.joinToString("") {
            "<e2webifexternal><e2path>$it</e2path></e2webifexternal>"
        } + "</e2webifexternals>"

    private companion object {
        const val TIMEOUT = 5_000L
        const val OTHER_PROFILE_ID = 8
        const val EXTERNALS = "/web/external"
    }
}
