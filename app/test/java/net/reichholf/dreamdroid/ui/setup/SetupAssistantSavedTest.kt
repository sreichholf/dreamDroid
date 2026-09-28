package net.reichholf.dreamdroid.ui.setup

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetupAssistantSavedTest {
    @Test
    fun absentSnapshotReadsFreshWizardWithoutWriting() {
        val handle = SavedStateHandle()
        assertEquals(SetupDraft(), readSetupDraft(handle))
        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun roundTripKeepsEveryWizardField() {
        val handle = SavedStateHandle()
        val draft = SetupDraft(
            step = SetupStep.SignIn,
            useHttps = true,
            login = false,
            nameEdited = true,
            trustAllCerts = true,
            suggestedName = "dm920",
            askedForNetwork = true
        )
        draft.writeTo(handle)
        assertEquals(draft, readSetupDraft(handle))
    }

    @Test
    fun unknownStepReadsAsWelcome() {
        val handle = SavedStateHandle(mapOf(SetupAssistantSavedKeys.STEP to "Gone"))
        assertEquals(SetupStep.Welcome, readSetupDraft(handle).step)
    }
}
