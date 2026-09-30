package net.reichholf.dreamdroid.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
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

    @Test
    fun theSwitchSendsOnlyItsGroupAfterTheGuard() = runBlocking<Unit> {
        receiver.respond(
            EDIT,
            TestReceiver.simpleResult(true, "AutoTimer wurde erfolgreich geändert")
        )
        val wilsberg = loaded()

        val result = repository.setEnabled(wilsberg, enabled = false)

        assertEquals(
            AutoTimerWriteResult.Done(UiText.Raw("AutoTimer wurde erfolgreich geändert")),
            result
        )
        val edit = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals(
            listOf("id", "match", "name", "enabled"),
            edit.queryParameterNames.toList()
        )
        assertEquals("2", edit.queryParameter("id"))
        assertEquals("0", edit.queryParameter("enabled"))
        // The guard listed the AutoTimers right before the write.
        assertEquals(
            listOf(LIST, EDIT),
            receiver.requests.takeLast(2).map { it.requestUrl!!.encodedPath }
        )
    }

    @Test
    fun aPercentSignReachesThePluginEscapedOnce() = runBlocking<Unit> {
        receiver.respond(EDIT, TestReceiver.simpleResult(true, "ok"))
        val wilsberg = loaded()

        repository.save(
            AutoTimerWrite.Change(wilsberg, wilsberg.settings.copy(match = "50%20"))
        )

        // The web server decodes once, the plugin once more: 50%2520 -> 50%20.
        val edit = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals("50%2520", edit.queryParameter("match"))
    }

    @Test
    fun aRenumberedListIsAConflictAndWritesNothing() = runBlocking<Unit> {
        val wilsberg = loaded()
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml").replace(
                "id=\"1\"",
                "id=\"2\""
            )
        )

        assertEquals(AutoTimerWriteResult.Conflict, repository.setEnabled(wilsberg, false))
        assertEquals(
            AutoTimerWriteResult.Conflict,
            repository.remove(AutoTimerEntry.Readable(wilsberg))
        )
        assertEquals(emptyList<Any>(), receiver.requestsTo(EDIT) + receiver.requestsTo(REMOVE))
    }

    @Test
    fun removeSendsTheIdAfterTheGuard() = runBlocking<Unit> {
        receiver.respond(REMOVE, loadWebFixture("autotimer/result_remove.xml"))
        val entry = AutoTimerEntry.Readable(loaded())

        val result = repository.remove(entry)

        assertEquals(AutoTimerWriteResult.Done(UiText.Raw("AutoTimer wurde entfernt")), result)
        assertEquals("2", receiver.requestsTo(REMOVE).single().requestUrl!!.queryParameter("id"))
    }

    @Test
    fun aRejectedWriteFailsWithTheBoxText() = runBlocking<Unit> {
        receiver.respond(
            EDIT,
            TestReceiver.simpleResult(false, "autotimers need a match attribute")
        )

        val result = repository.setEnabled(loaded(), enabled = false)

        assertEquals(
            AutoTimerWriteResult.Failed(UiText.Raw("autotimers need a match attribute")),
            result
        )
    }

    private suspend fun loaded(): AutoTimer {
        val ready = repository.list() as AutoTimerLoad.Ready
        return (ready.entries.single() as AutoTimerEntry.Readable).autoTimer
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
        const val EDIT = "/autotimer/edit"
        const val REMOVE = "/autotimer/remove"
    }
}
