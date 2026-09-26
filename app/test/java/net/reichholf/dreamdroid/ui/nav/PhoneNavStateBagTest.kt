package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNavStateBagTest {
    @Test
    fun emptyAccessReadsUnsetStartRouteAndDefaults() {
        val bag = readPhoneNavStateBag(MapPhoneNavPlainAccess())

        assertNull(bag.startRoute)
        assertFalse(bag.hasSavedStartRoute())
        assertEquals(emptyList<Int>(), bag.pickRequestCodes)
    }

    @Test
    fun filledBagRoundTripsThroughPlainAccess() {
        val original = PhoneNavStateBag(
            startRoute = PhoneNavRoutes.EPG,
            pickRequestCodes = listOf(7, 1, 7)
        )
        val access = MapPhoneNavPlainAccess()

        original.writePlain(access)

        assertEquals(original, readPhoneNavStateBag(access))
    }

    @Test
    fun nullStartRouteLeavesTheKeyAbsent() {
        val access = MapPhoneNavPlainAccess()
        access.putString("unrelated", "kept")

        PhoneNavStateBag().writePlain(access)

        assertFalse(access.contains(PhoneNavSavedKeys.START_ROUTE))
        assertEquals(emptyList<Int>(), access.getIntList(PhoneNavSavedKeys.PICK_REQUEST_CODES))
        assertEquals("kept", access.getString("unrelated"))
    }

    @Test
    fun laterNullStartRouteClearsSavedStart() {
        val access = MapPhoneNavPlainAccess()
        PhoneNavStateBag(startRoute = PhoneNavRoutes.REMOTE).writePlain(access)
        assertTrue(access.contains(PhoneNavSavedKeys.START_ROUTE))

        PhoneNavStateBag(startRoute = null).writePlain(access)

        assertFalse(access.contains(PhoneNavSavedKeys.START_ROUTE))
        assertFalse(readPhoneNavStateBag(access).hasSavedStartRoute())
    }
}
