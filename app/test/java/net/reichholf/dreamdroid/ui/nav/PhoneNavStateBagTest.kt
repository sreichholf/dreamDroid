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
        assertEquals(PhoneNavRoutes.PROFILE_EDIT, bag.profileEditTag)
        assertEquals(PhoneNavRoutes.TIMER_EDIT, bag.timerEditTag)
        assertNull(bag.epgRef)
        assertNull(bag.epgName)
        assertNull(bag.epgFocusedRef)
        assertNull(bag.epgTimeSec)
    }

    @Test
    fun filledBagRoundTripsThroughPlainAccess() {
        val original = PhoneNavStateBag(
            startRoute = PhoneNavRoutes.EPG,
            pickRequestCodes = listOf(7, 1, 7),
            profileEditTag = "profile_tag",
            timerEditTag = "timer_tag",
            epgRef = "1:0:1:ref",
            epgName = "News",
            epgFocusedRef = "1:0:1:focused",
            epgTimeSec = 0L
        )
        val access = MapPhoneNavPlainAccess()

        original.writePlain(access)

        assertEquals(original, readPhoneNavStateBag(access))
    }

    @Test
    fun nullStartRouteAndEpgTimeLeaveKeysAbsent() {
        val access = MapPhoneNavPlainAccess()
        access.putString(PhoneNavSavedKeys.PROFILE_EDIT_ARGS, "bundle-stand-in")
        access.putString(PhoneNavSavedKeys.TIMER_EDIT_ARGS, "bundle-stand-in")

        PhoneNavStateBag().writePlain(access)

        assertFalse(access.contains(PhoneNavSavedKeys.START_ROUTE))
        assertFalse(access.contains(PhoneNavSavedKeys.EPG_TIME_SEC))
        assertFalse(access.contains(PhoneNavSavedKeys.EPG_REF))
        assertFalse(access.contains(PhoneNavSavedKeys.EPG_NAME))
        assertFalse(access.contains(PhoneNavSavedKeys.EPG_FOCUSED_REF))
        assertEquals(emptyList<Int>(), access.getIntList(PhoneNavSavedKeys.PICK_REQUEST_CODES))
        assertEquals("bundle-stand-in", access.getString(PhoneNavSavedKeys.PROFILE_EDIT_ARGS))
        assertEquals("bundle-stand-in", access.getString(PhoneNavSavedKeys.TIMER_EDIT_ARGS))
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
