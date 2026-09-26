package net.reichholf.dreamdroid.ui.nav

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class EpgSearchRouteTest {
    @Test
    fun queryIsAnOptionalArgumentNotAPathSegment() {
        val descriptor = EpgSearch.serializer().descriptor
        assertEquals(PhoneNavRoutes.EPG_SEARCH, descriptor.serialName)
        assertEquals("query", descriptor.getElementName(0))
        assertTrue(descriptor.isElementOptional(0))
        assertFalse(descriptor.elementNames.any { it.contains("/") })
    }

    @Test
    fun slashQuestionAndSpaceStayOnTheRouteValue() {
        val queries = listOf(
            "Tagesschau / Wetter",
            "who? what",
            "two words",
            "Tagesschau / Wetter? live"
        )
        for (query in queries) {
            assertEquals(query, EpgSearch(query).query)
            assertFalse(query.contains('\u0000'))
        }
    }

    @Test
    fun emptyQueryKeepsTheSearchRoute() {
        assertEquals("", EpgSearch().query)
        assertEquals(PhoneNavRoutes.EPG_SEARCH, EpgSearch.serializer().descriptor.serialName)
    }

    @Test
    fun serviceRefIsARequiredPathArgument() {
        val descriptor = ServiceEpg.serializer().descriptor
        assertEquals(PhoneNavRoutes.SERVICE_EPG, descriptor.serialName)
        assertEquals("serviceRef", descriptor.getElementName(0))
        assertFalse(descriptor.isElementOptional(0))
        assertTrue(descriptor.isElementOptional(1))
    }
}
