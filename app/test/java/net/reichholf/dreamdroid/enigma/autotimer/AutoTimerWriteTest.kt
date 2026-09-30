package net.reichholf.dreamdroid.enigma.autotimer

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AutoTimerWriteTest {
    private val full = load("list_disabled_full.xml")

    @Test
    fun anUnchangedAutoTimerSendsOnlyIdMatchAndName() {
        val write = AutoTimerWrite.Change(full, full.settings)

        assertEquals(emptySet<FieldGroup>(), write.groups)
        assertEquals(
            listOf("id" to "1", "match" to "Wilsberg", "name" to "dreamDroid test Wilsberg"),
            write.pairs()
        )
    }

    @Test
    fun theCapturedPartialEditEncodesAsTheChangedGroups() {
        val edited = load("list_after_partial_edit.xml").settings

        val write = AutoTimerWrite.Change(full, edited)

        assertEquals(
            setOf(FieldGroup.TimeWindow, FieldGroup.Offset, FieldGroup.Tags),
            write.groups
        )
        assertEquals(
            listOf(
                "id" to "1",
                "match" to "Wilsberg",
                "name" to "dreamDroid test Wilsberg",
                "timespanFrom" to "",
                "timespanTo" to "",
                "offset" to "3,3",
                "tag" to "dreamdroid-post"
            ),
            write.pairs()
        )
    }

    @Test
    fun eachGroupSendsItsKeys() {
        val cases: List<Pair<AutoTimerSettings, List<Pair<String, String>>>> = listOf(
            full.settings.copy(enabled = true) to listOf("enabled" to "1"),
            full.settings.copy(searchType = SearchType.Exact, caseSensitive = true) to listOf(
                "searchType" to "exact",
                "searchCase" to "sensitive"
            ),
            full.settings.copy(timeWindow = ClockWindow(LocalTime.of(8, 5), LocalTime.of(0, 0))) to
                listOf("timespanFrom" to "08:05", "timespanTo" to "00:00"),
            full.settings.copy(
                dateWindow = DateWindow(Instant.ofEpochSecond(10), Instant.ofEpochSecond(20))
            ) to listOf("after" to "10", "before" to "20"),
            full.settings.copy(offset = null) to listOf("offset" to ""),
            full.settings.copy(maxDurationMinutes = null) to listOf("maxduration" to ""),
            full.settings.copy(location = "/media/hdd/movie/") to
                listOf("location" to "/media/hdd/movie/"),
            full.settings.copy(targets = listOf(full.settings.targets[2])) to listOf(
                "services" to "",
                "bouquets" to full.settings.targets[2].ref
            ),
            full.settings.copy(tags = emptyList()) to listOf("tag" to ""),
            full.settings.copy(tags = listOf("a", "b")) to listOf("tag" to "a", "tag" to "b"),
            full.settings.copy(include = Filters(title = listOf("A"))) to listOf(
                "title" to "A",
                "shortdescription" to "",
                "description" to "",
                "dayofweek" to ""
            ),
            full.settings.copy(
                exclude = Filters(days = listOf(DayFilter.On(DayOfWeek.MONDAY), DayFilter.Weekend))
            ) to listOf(
                "!title" to "",
                "!shortdescription" to "",
                "!description" to "",
                "!dayofweek" to "0",
                "!dayofweek" to "weekend"
            ),
            full.settings.copy(afterEvent = AfterEvent.ReceiverDefault) to
                listOf("afterevent" to "default"),
            full.settings.copy(afterEvent = AfterEvent.Fixed(AfterEventAction.DeepStandby)) to
                listOf("afterevent" to "deepstandby"),
            full.settings.copy(
                afterEvent = AfterEvent.Fixed(
                    AfterEventAction.Nothing,
                    ClockWindow(LocalTime.of(22, 0), LocalTime.of(6, 30))
                )
            ) to listOf(
                "afterevent" to "nothing",
                "aftereventFrom" to "22:00",
                "aftereventTo" to "06:30"
            ),
            full.settings.copy(recordMode = RecordMode.Zap(setEndTime = false)) to
                listOf("justplay" to "1", "setEndtime" to "0"),
            full.settings.copy(duplicates = DuplicateCheck.Off) to
                listOf("avoidDuplicateDescription" to "0"),
            full.settings.copy(
                duplicates = DuplicateCheck.On(DuplicateScope.AnyService, DescriptionCompare.Title)
            ) to listOf(
                "avoidDuplicateDescription" to "2",
                "searchForDuplicateDescription" to "0"
            )
        )

        cases.forEach { (edited, expected) ->
            assertEquals(
                expected,
                AutoTimerWrite.Change(full, edited).pairs().drop(3),
                "for $edited"
            )
        }
    }

    @Test
    fun backToRecordingSendsJustplayOnly() {
        val zap = full.copy(settings = full.settings.copy(recordMode = RecordMode.Zap(true)))

        val write = AutoTimerWrite.Change(zap, zap.settings.copy(recordMode = RecordMode.Record))

        assertEquals(listOf("justplay" to "0"), write.pairs().drop(3))
    }

    @Test
    fun percentSignsAreEscapedWhereThePluginDecodesTwice() {
        val edited = full.settings.copy(
            match = "50%20",
            name = "100%",
            tags = listOf("a%b"),
            location = "/media/100%/",
            exclude = Filters(title = listOf("x%y"))
        )

        val pairs = AutoTimerWrite.Change(full, edited).pairs().toMap()

        assertEquals("50%2520", pairs["match"])
        assertEquals("100%25", pairs["name"])
        assertEquals("a%25b", pairs["tag"])
        assertEquals("/media/100%/", pairs["location"])
        assertEquals("x%25y", pairs["!title"])
    }

    @Test
    fun severalAfterEventsAreNeverSent() {
        val several = AfterEvent.Several(
            listOf(
                AfterEvent.Fixed(AfterEventAction.Auto),
                AfterEvent.Fixed(AfterEventAction.Standby)
            )
        )

        val write = AutoTimerWrite.Change(full, full.settings.copy(afterEvent = several))

        assertFalse(FieldGroup.AfterEvent in write.groups)
    }

    @Test
    fun targetsWithACommaCannotBeSent() {
        val edited = full.settings.copy(
            targets = listOf(Target.Channel("4097:0:1:0:0:0:0:0:0:0:http%3a//a,b:", "Odd"))
        )

        assertFalse(edited.targetsSendable)
        assertFalse(FieldGroup.Targets in AutoTimerWrite.Change(full, edited).groups)
    }

    @Test
    fun aCreateSendsWhatDiffersFromTheDefaultsAndNoId() {
        val defaults = AutoTimerSettings.NEW.copy(offset = Offset(5, 10))
        val edited = defaults.copy(
            match = "Tatort",
            name = "",
            include = Filters(days = listOf(DayFilter.On(DayOfWeek.SUNDAY)))
        )

        val write = AutoTimerWrite.Create(defaults, edited)

        assertEquals(
            listOf(
                "match" to "Tatort",
                "name" to "",
                "title" to "",
                "shortdescription" to "",
                "description" to "",
                "dayofweek" to "6"
            ),
            write.pairs()
        )
    }

    private fun AutoTimerWrite.pairs(): List<Pair<String, String>> =
        toParams().map { it.key to it.value() }

    private fun load(fixture: String): AutoTimer = (
        AutoTimerListParser.parse(loadWebFixture("autotimer/$fixture"))!!.entries.single()
            as AutoTimerEntry.Readable
        ).autoTimer
}
