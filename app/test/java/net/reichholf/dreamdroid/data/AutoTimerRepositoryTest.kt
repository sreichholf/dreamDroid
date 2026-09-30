package net.reichholf.dreamdroid.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutoTimerRepositoryTest {
    private val receiver = TestReceiver()
    private val repository =
        AutoTimerRepository(enigmaClients(receiver.repository), receiver.repository)

    @BeforeEach
    fun setUp() {
        receiver.start()
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_enabled.xml"))
    }

    @AfterEach
    fun tearDown() {
        receiver.shutdown()
    }

    @Test
    fun presentWhenWebExternalsListTheAutoTimerPlugin() = runBlocking<Unit> {
        assertEquals(PluginPresence.Unknown, repository.presence.first())

        assertEquals(PluginPresence.Present, repository.refreshPresence())
        assertEquals(PluginPresence.Present, repository.presence.first())
    }

    @Test
    fun theWebEditorAloneIsNotThePlugin() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, externals("autotimereditor", "bouqueteditor"))

        assertEquals(PluginPresence.Absent, repository.refreshPresence())
        assertEquals(AutoTimerLoad.PluginMissing, repository.list())
        assertEquals(emptyList<Any>(), receiver.requestsTo(LIST))
    }

    @Test
    fun aFailedCheckKeepsTheLastAnswer() = runBlocking<Unit> {
        repository.refreshPresence()
        receiver.fail(EXTERNALS)

        assertEquals(PluginPresence.Present, repository.refreshPresence())
    }

    @Test
    fun presenceBelongsToTheProfile() = runBlocking<Unit> {
        repository.refreshPresence()
        val other = receiver.repository.requireCurrent()
        receiver.repository.setCurrent(
            Profile().apply {
                id = OTHER_PROFILE_ID
                name = "other"
                host = other.host
                port = other.port
            }
        )

        withTimeout(TIMEOUT) { repository.presence.first { it == PluginPresence.Unknown } }
    }

    @Test
    fun listsAfterOneCheck() = runBlocking<Unit> {
        val first = repository.list() as AutoTimerLoad.Ready
        repository.list()

        assertEquals(listOf(AutoTimerId(2)), first.entries.map { it.id })
        assertEquals(1, receiver.requestsTo(EXTERNALS).size)
        assertEquals(2, receiver.requestsTo(LIST).size)
    }

    @Test
    fun aFailedCheckIsAFailureNotAMissingPlugin() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, externals("bouqueteditor"))
        repository.refreshPresence()
        receiver.fail(EXTERNALS)

        assertEquals(true, repository.list() is AutoTimerLoad.Failed)
    }

    @Test
    fun aConfigTheBoxCannotLoadShowsItsText() = runBlocking<Unit> {
        receiver.respond(
            LIST,
            TestReceiver.simpleResult(false, "Couldn't load config file!")
        )

        val failed = repository.list() as AutoTimerLoad.Failed

        assertEquals(
            UiText.Resource(
                R.string.content_error_detail,
                listOf(
                    UiText.Resource(R.string.get_content_error),
                    EnigmaFailure.BoxRejected("Couldn't load config file!").userMessageText()
                )
            ),
            failed.message
        )
    }

    private fun externals(vararg paths: String): String =
        "<e2webifexternals>" + paths.joinToString("") {
            "<e2webifexternal><e2path>$it</e2path></e2webifexternal>"
        } + "</e2webifexternals>"

    private companion object {
        const val TIMEOUT = 5_000L
        const val OTHER_PROFILE_ID = 8
        const val EXTERNALS = "/web/external"
        const val LIST = "/autotimer"
    }
}
