package net.reichholf.dreamdroid.enigma.autotimer

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `/autotimer` replies captured from a dm900 with AutoTimer 4.3.2, plus hand-written cases. */
class AutoTimerListParserTest {
    @Test
    fun emptyListHasOnlyTheDefaultsBlock() {
        assertEquals(emptyList<AutoTimerEntry>(), parse("list_empty.xml"))
    }

    @Test
    fun readsEveryFieldOfTheFullFixture() {
        val entry = parse("list_disabled_full.xml").single() as AutoTimerEntry.Readable
        val autoTimer = entry.autoTimer

        assertEquals(AutoTimerId(1), autoTimer.id)
        assertEquals(
            AutoTimerSettings(
                name = "dreamDroid test Wilsberg",
                match = "Wilsberg",
                enabled = false,
                searchType = SearchType.Partial,
                caseSensitive = false,
                targets = listOf(
                    Target.Channel("1:0:19:2B66:3F3:1:C00000:0:0:0:", "ZDF HD"),
                    Target.Channel("1:0:19:2B7A:3F3:1:C00000:0:0:0:", "zdf_neo HD"),
                    Target.Bouquet(
                        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" " +
                            "ORDER BY bouquet",
                        "Favourites (TV)"
                    )
                ),
                timeWindow = ClockWindow(LocalTime.of(18, 0), LocalTime.of(23, 30)),
                dateWindow = null,
                offset = Offset(5, 10),
                maxDurationMinutes = 120,
                location = null,
                tags = listOf("dreamdroid-test"),
                include = Filters(
                    days = listOf(DayFilter.On(DayOfWeek.SATURDAY), DayFilter.On(DayOfWeek.SUNDAY))
                ),
                exclude = Filters(title = listOf("Vorschau"), description = listOf("Wiederholung")),
                afterEvent = AfterEvent.Fixed(AfterEventAction.Standby),
                recordMode = RecordMode.Record,
                duplicates = DuplicateCheck.On(DuplicateScope.SameService, DescriptionCompare.All)
            ),
            autoTimer.settings
        )
        assertEquals(Extras(), autoTimer.extras)
    }

    @Test
    fun readsTheEnabledFixture() {
        val settings = readable(parse("list_enabled.xml").single()).settings

        assertTrue(settings.enabled)
        assertEquals(ClockWindow(LocalTime.of(20, 0), LocalTime.of(23, 0)), settings.timeWindow)
        assertEquals(listOf("ZDF HD", "zdf_neo HD"), settings.targets.map { it.name })
        assertEquals(DuplicateCheck.Off, settings.duplicates)
        assertEquals(AfterEvent.ReceiverDefault, settings.afterEvent)
        assertNull(settings.offset)
    }

    @Test
    fun partialEditFixtureDiffersInOffsetWindowAndTags() {
        val before = readable(parse("list_disabled_full.xml").single()).settings
        val after = readable(parse("list_after_partial_edit.xml").single()).settings

        assertEquals(
            before.copy(offset = Offset(3, 3), timeWindow = null, tags = listOf("dreamdroid-post")),
            after
        )
    }

    @Test
    fun afterEventTokensWindowsAndSeveral() {
        assertEquals(
            AfterEvent.Fixed(AfterEventAction.DeepStandby),
            single("""<afterevent>shutdown</afterevent>""").afterEvent
        )
        assertEquals(
            AfterEvent.Fixed(AfterEventAction.DeepStandby),
            single("""<afterevent>deepstandby</afterevent>""").afterEvent
        )
        assertEquals(
            AfterEvent.Fixed(
                AfterEventAction.Nothing,
                ClockWindow(LocalTime.of(22, 0), LocalTime.of(6, 30))
            ),
            single("""<afterevent from="22:00" to="06:30">none</afterevent>""").afterEvent
        )
        assertEquals(
            AfterEvent.Several(
                listOf(
                    AfterEvent.Fixed(AfterEventAction.Auto),
                    AfterEvent.Fixed(AfterEventAction.Standby)
                )
            ),
            single("<afterevent>auto</afterevent><afterevent>standby</afterevent>").afterEvent
        )
    }

    @Test
    fun anUnknownValueMakesOnlyThatEntryUnreadable() {
        val entries = AutoTimerListParser.parse(
            list(
                timer(id = 1, children = "<afterevent>hibernate</afterevent>") +
                    timer(id = 2)
            )
        )!!.entries

        val unreadable = entries[0] as AutoTimerEntry.Unreadable
        assertEquals(AutoTimerId(1), unreadable.id)
        assertEquals("T", unreadable.name)
        assertTrue(entries[1] is AutoTimerEntry.Readable)
    }

    @Test
    fun aTimerWithoutIdIsSkipped() {
        val xml = list("""<timer name="T" match="T" enabled="yes"></timer>""")

        assertEquals(emptyList<AutoTimerEntry>(), AutoTimerListParser.parse(xml)!!.entries)
    }

    @Test
    fun zapModeSearchDatesAndExtras() {
        val entry = AutoTimerListParser.parse(
            list(
                timer(
                    id = 3,
                    attributes = """ justplay="1" setEndtime="0" searchType="exact" """ +
                        """searchCase="sensitive" after="1790000000" before="1791000000" """ +
                        """location="/media/hdd/series/" counter="5" left="3" """ +
                        """vps_enabled="yes" series_labeling="yes" overrideAlternatives="1" """ +
                        """avoidDuplicateDescription="3" searchForDuplicateDescription="0" """,
                    children = """<include where="dayofweek">weekend</include>""" +
                        """<exclude where="dayofweek">weekday</exclude>""" +
                        """<exclude where="shortdescription">Teil 1</exclude>""" +
                        "<e2tags>a b</e2tags>"
                )
            )
        )!!.entries.single()
        val autoTimer = readable(entry)
        val settings = autoTimer.settings

        assertEquals(RecordMode.Zap(setEndTime = false), settings.recordMode)
        assertEquals(SearchType.Exact, settings.searchType)
        assertTrue(settings.caseSensitive)
        assertEquals(
            DateWindow(Instant.ofEpochSecond(1790000000), Instant.ofEpochSecond(1791000000)),
            settings.dateWindow
        )
        assertEquals("/media/hdd/series/", settings.location)
        assertEquals(
            DuplicateCheck.On(DuplicateScope.AnyServiceOrRecording, DescriptionCompare.Title),
            settings.duplicates
        )
        assertEquals(listOf(DayFilter.Weekend), settings.include.days)
        assertEquals(
            Filters(shortDescription = listOf("Teil 1"), days = listOf(DayFilter.Weekdays)),
            settings.exclude
        )
        assertEquals(listOf("a", "b"), settings.tags)
        assertEquals(Extras(true, true, true, true), autoTimer.extras)
    }

    @Test
    fun attributesDecodeEntities() {
        val xml = list(
            """<timer name="Tom &amp; Jerry" match="&quot;Tom&quot; &lt;3" """ +
                """enabled="yes" id="4"></timer>"""
        )

        val settings = readable(AutoTimerListParser.parse(xml)!!.entries.single()).settings

        assertEquals("Tom & Jerry", settings.name)
        assertEquals("\"Tom\" <3", settings.match)
    }

    @Test
    fun emptyDefaultsAreThePluginsOwn() {
        val list = AutoTimerListParser.parse(loadWebFixture("autotimer/list_empty.xml"))!!

        assertEquals(AutoTimerSettings.NEW, list.defaults)
    }

    @Test
    fun defaultsCarryTheirSettings() {
        val xml = """<?xml version="1.0" ?><autotimer version="8">""" +
            """<defaults id="-1" from="16:30" to="23:15" offset="5,10">""" +
            "<e2service><e2servicereference>1:7:1:0:0:0:0:0:0:0:FROM BOUQUET</e2servicereference>" +
            "<e2servicename>Favourites (TV)</e2servicename></e2service>" +
            "</defaults></autotimer>"

        val defaults = AutoTimerListParser.parse(xml)!!.defaults

        assertEquals(
            AutoTimerSettings.NEW.copy(
                timeWindow = ClockWindow(LocalTime.of(16, 30), LocalTime.of(23, 15)),
                offset = Offset(5, 10),
                targets = listOf(
                    Target.Bouquet("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", "Favourites (TV)")
                )
            ),
            defaults
        )
    }

    @Test
    fun unreadableDefaultsAreNull() {
        val xml = """<autotimer version="8"><defaults id="-1" offset="x"></defaults></autotimer>"""

        assertNull(AutoTimerListParser.parse(xml)!!.defaults)
    }

    @Test
    fun aSimpleResultIsNotAList() {
        val xml = "<e2simplexmlresult><e2state>False</e2state>" +
            "<e2statetext>Couldn't load config file!</e2statetext></e2simplexmlresult>"

        assertNull(AutoTimerListParser.parse(xml))
    }

    private fun parse(fixture: String): List<AutoTimerEntry> =
        AutoTimerListParser.parse(loadWebFixture("autotimer/$fixture"))!!.entries

    private fun readable(entry: AutoTimerEntry): AutoTimer =
        (entry as AutoTimerEntry.Readable).autoTimer

    private fun single(children: String): AutoTimerSettings = readable(
        AutoTimerListParser.parse(list(timer(id = 1, children = children)))!!.entries.single()
    )
        .settings

    private fun timer(id: Int, attributes: String = "", children: String = ""): String =
        """<timer name="T" match="T" enabled="yes" id="$id"$attributes>$children</timer>"""

    private fun list(timers: String): String =
        """<?xml version="1.0" ?><autotimer version="8"><defaults id="-1"></defaults>""" +
            "$timers</autotimer>"
}
