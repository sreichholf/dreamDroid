package net.reichholf.dreamdroid.ui.setup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetupAssistantSavedTest {
    @Test
    fun absentSnapshotReadsFreshWizardWithoutWriting() {
        val values = mutableMapOf<String, Any>()
        assertEquals(SetupDraft(), readSetupDraft(MapSetupAssistantSavedAccess(values)))
        assertTrue(values.isEmpty())
    }

    @Test
    fun roundTripKeepsEveryWizardField() {
        val access = MapSetupAssistantSavedAccess()
        val draft = SetupDraft(
            step = SetupStep.SignIn,
            host = "192.168.1.2",
            useHttps = true,
            portText = "8443",
            login = false,
            user = "admin",
            pass = "secret",
            profileName = "Living room",
            nameEdited = true,
            trustAllCerts = true,
            suggestedName = "dm920",
            askedForNetwork = true
        )
        draft.writeTo(access)
        assertEquals(draft, readSetupDraft(access))
    }

    @Test
    fun unknownStepReadsAsWelcome() {
        val values = mutableMapOf<String, Any>(SetupAssistantSavedKeys.STEP to "Gone")
        assertEquals(
            SetupStep.Welcome,
            readSetupDraft(MapSetupAssistantSavedAccess(values)).step
        )
    }
}
