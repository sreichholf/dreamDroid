package net.reichholf.dreamdroid.ui.nav

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNavStateBagTest {
    @Test
    fun emptyHandleReadsUnsetStartRouteAndDefaults() {
        val bag = readPhoneNavStateBag(SavedStateHandle())

        assertNull(bag.startRoute)
        assertFalse(bag.hasSavedStartRoute())
        assertEquals(emptyList<Int>(), bag.pickRequestCodes)
    }

    @Test
    fun filledBagRoundTripsThroughHandle() {
        val original = PhoneNavStateBag(
            startRoute = PhoneNavRoutes.EPG,
            pickRequestCodes = listOf(7, 1, 7)
        )
        val handle = SavedStateHandle()

        original.writePlain(handle)

        assertEquals(original, readPhoneNavStateBag(handle))
    }

    @Test
    fun nullStartRouteLeavesTheKeyAbsent() {
        val handle = SavedStateHandle()
        handle["unrelated"] = "kept"

        PhoneNavStateBag().writePlain(handle)

        assertFalse(handle.contains(PhoneNavSavedKeys.START_ROUTE))
        assertEquals(
            emptyList<Int>(),
            handle.get<IntArray>(PhoneNavSavedKeys.PICK_REQUEST_CODES)?.toList()
        )
        assertEquals("kept", handle.get<String>("unrelated"))
    }

    @Test
    fun laterNullStartRouteClearsSavedStart() {
        val handle = SavedStateHandle()
        PhoneNavStateBag(startRoute = PhoneNavRoutes.REMOTE).writePlain(handle)
        assertTrue(handle.contains(PhoneNavSavedKeys.START_ROUTE))

        PhoneNavStateBag(startRoute = null).writePlain(handle)

        assertFalse(handle.contains(PhoneNavSavedKeys.START_ROUTE))
        assertFalse(readPhoneNavStateBag(handle).hasSavedStartRoute())
    }
}
