package net.reichholf.dreamdroid.ui.services

import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import org.junit.Assert.assertEquals
import org.junit.Test

class HubServiceListSessionTest {
    @Test
    fun staleReloadDoesNotReplaceNewerServiceList() {
        val session = HubServiceListSession()
        val state = ServiceListState()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.listState = state
        session.refresh = ComposeRefreshState()

        val staleGeneration = session.beginLoad()
        val freshGeneration = session.beginLoad()
        session.applyLoadResult(
            generation = staleGeneration,
            success = true,
            rows = listOf(
                ServiceNowNext(
                    serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                    serviceName = "Stale bouquet",
                ),
            ),
            errorText = null,
        )
        assertEquals(emptyList<String>(), state.items.map { it.name })

        session.applyLoadResult(
            generation = freshGeneration,
            success = true,
            rows = listOf(
                ServiceNowNext(
                    serviceReference = "1:0:1:2:1:1:1:0:0:0:",
                    serviceName = "Fresh bouquet",
                ),
            ),
            errorText = null,
        )
        assertEquals(listOf("Fresh bouquet"), state.items.map { it.name })

        session.applyLoadResult(
            generation = staleGeneration,
            success = false,
            rows = emptyList(),
            errorText = "timeout",
        )
        assertEquals(listOf("Fresh bouquet"), state.items.map { it.name })
    }
}
