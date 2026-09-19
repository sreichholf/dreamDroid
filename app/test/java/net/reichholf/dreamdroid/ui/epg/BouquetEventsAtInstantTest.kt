package net.reichholf.dreamdroid.ui.epg

import net.reichholf.dreamdroid.room.EpgEventEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BouquetEventsAtInstantTest {
    @Test
    fun oneProgrammePerChannelAtSelectedInstant() {
        val news = entity("News", CHANNEL, start = NOW, duration = 3600)
        val talk = entity("Talk", CHANNEL, start = NOW + 3600, duration = 1800)
        val match = entity("Match", OTHER, start = NOW - 60, duration = 7200)
        val selected = bouquetEventsAtInstant(listOf(news, talk, match), NOW)
        assertEquals(listOf("News", "Match"), selected.map { it.title })
    }

    @Test
    fun eventStartingAtInstantIsIncluded() {
        val onTheHour = entity("News", CHANNEL, start = NOW, duration = 900)
        assertEquals(
            listOf("News"),
            bouquetEventsAtInstant(listOf(onTheHour), NOW).map { it.title }
        )
    }

    @Test
    fun eventEndingAtInstantIsExcluded() {
        val ended = entity("News", CHANNEL, start = NOW - 3600, duration = 3600)
        val next = entity("Talk", CHANNEL, start = NOW, duration = 1800)
        assertEquals(
            listOf("Talk"),
            bouquetEventsAtInstant(listOf(ended, next), NOW).map { it.title }
        )
    }

    @Test
    fun channelWithNoOverlapIsOmitted() {
        val later = entity("Late", CHANNEL, start = NOW + 7200, duration = 1800)
        assertEquals(
            emptyList<String>(),
            bouquetEventsAtInstant(listOf(later), NOW).map {
                it.title
            }
        )
    }

    private fun entity(
        title: String,
        service: String,
        start: Long,
        duration: Long
    ): EpgEventEntity = EpgEventEntity(
        profileId = 7,
        bouquetRef = BOUQUET,
        serviceRef = service,
        eventId = title,
        start = start,
        duration = duration,
        title = title,
        description = "",
        descriptionExtended = "",
        serviceName = title
    )

    companion object {
        private const val NOW = 1_893_456_000L
        private const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        private const val CHANNEL = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
        private const val OTHER = "1:0:1:6DCB:44C:1:C00000:0:0:0:"
    }
}
