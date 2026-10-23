package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HubSectionTest {
    private val rows = listOf(row(FAVOURITES, "Favourites"), row(SPORTS, ""))
    private val locations = listOf(HDD, USB)

    @Test
    fun guideNeedsBouquetsAndRecordingsNeedLocations() {
        assertEquals(
            listOf(HubTab.Live, HubTab.Guide, HubTab.Recordings, HubTab.Timers),
            hubTabs(rows, locations)
        )
        assertEquals(listOf(HubTab.Live, HubTab.Timers), hubTabs(emptyList(), emptyList()))
    }

    @Test
    fun eachSectionDerivesItsTabDrawerAndTitle() {
        val bouquets = listOf(HubDrawerItem(FAVOURITES, "Favourites"), HubDrawerItem(SPORTS, "?"))

        assertEquals(HubTab.Live, HubSection.Live(FAVOURITES).tab)
        assertEquals(bouquets, HubSection.Live(FAVOURITES).drawerItems(rows, locations, "?"))
        assertEquals(UiText.Raw("Favourites"), HubSection.Live(FAVOURITES).title(rows))
        assertEquals(UiText.Resource(R.string.services), HubSection.Live(SPORTS).title(rows))

        assertEquals(HubTab.Guide, HubSection.Guide(SPORTS).tab)
        assertNull(HubSection.Guide(SPORTS).title(rows))

        val recordings = HubSection.Recordings(USB)
        assertEquals(HubTab.Recordings, recordings.tab)
        assertEquals(
            listOf(HubDrawerItem(HDD, HDD), HubDrawerItem(USB, USB)),
            recordings.drawerItems(rows, locations, "?")
        )
        assertEquals(UiText.Raw(USB), recordings.title(rows))

        assertEquals(HubTab.Timers, HubSection.Timers.tab)
        assertNull(HubSection.Timers.drawerItems(rows, locations, "?"))
        assertNull(HubSection.Settings.tab)
        assertNull(HubSection.Settings.drawerItems(rows, locations, "?"))
        assertEquals(UiText.Resource(R.string.settings), HubSection.Settings.title(rows))
    }

    @Test
    fun liveTvWithoutBouquetsHasAnEmptyDrawer() {
        assertEquals(
            emptyList<HubDrawerItem>(),
            HubSection.Live(null).drawerItems(emptyList(), locations, "?")
        )
        assertNull(HubSection.Live(null).title(emptyList()))
    }

    /** The MultiEPG's bouquet chip is the Guide's only bouquet selector: no hub drawer. */
    @Test
    fun guideHasNoDrawerAndIsFullWidth() {
        val guide = HubSection.Guide(SPORTS)

        assertNull(guide.drawerItems(rows, locations, "?"))
        assertNull(guide.drawerSelection)
    }

    /** A bouquet picked with the Guide's chip moves the Guide and the Live TV bouquet. */
    @Test
    fun aBouquetPickedWithTheGuideChipSelectsTheGuideBouquet() {
        val guide = HubNavigation().reconcile(rows, locations)
            .open(HubTab.Guide, rows, locations)
            .select(SPORTS)

        assertEquals(HubSection.Guide(SPORTS), guide.section)
        assertEquals(SPORTS, guide.bouquetRef)
    }

    @Test
    fun guideOpensOnTheLiveBouquet() {
        val live = HubNavigation().reconcile(rows, locations).select(SPORTS)

        assertEquals(HubSection.Guide(SPORTS), live.open(HubTab.Guide, rows, locations).section)
    }

    @Test
    fun aBouquetPickedInTheGuideIsTheLiveBouquetToo() {
        val guide = HubNavigation().reconcile(rows, locations)
            .open(HubTab.Guide, rows, locations)
            .select(SPORTS)

        assertEquals(HubSection.Live(SPORTS), guide.open(HubTab.Live, rows, locations).section)
    }

    @Test
    fun tabsReturnToTheirLastRow() {
        val navigation = HubNavigation().reconcile(rows, locations)
            .select(SPORTS)
            .open(HubTab.Recordings, rows, locations)
        assertEquals(HubSection.Recordings(HDD), navigation.section)

        val back = navigation.select(USB)
            .open(HubTab.Timers, rows, locations)
            .openSettings()
            .open(HubTab.Recordings, rows, locations)
        assertEquals(HubSection.Recordings(USB), back.section)
        assertEquals(HubSection.Live(SPORTS), back.open(HubTab.Live, rows, locations).section)
    }

    @Test
    fun backReturnsToLiveTvOnItsBouquetAndLeavesFromLiveTv() {
        val live = HubNavigation().reconcile(rows, locations).select(SPORTS)
        assertNull(live.back())

        for (other in listOf(
            live.open(HubTab.Guide, rows, locations),
            live.open(HubTab.Recordings, rows, locations),
            live.open(HubTab.Timers, rows, locations),
            live.openSettings()
        )) {
            assertEquals(HubSection.Live(SPORTS), other.back()?.section)
        }
    }

    @Test
    fun drawerSelectionIsIgnoredWithoutADrawer() {
        val timers = HubNavigation().reconcile(rows, locations).open(HubTab.Timers, rows, locations)

        assertEquals(timers, timers.select(SPORTS))
    }

    @Test
    fun theHubOpensOnTheFirstBouquet() {
        assertEquals(
            HubSection.Live(FAVOURITES),
            HubNavigation().reconcile(rows, locations).section
        )
        assertEquals(
            HubSection.Live(null),
            HubNavigation().reconcile(emptyList(), locations).section
        )
        assertNull(HubNavigation().liveBouquetRef)
    }

    @Test
    fun aReloadKeepsWhatIsStillThere() {
        val sports = HubNavigation().reconcile(rows, locations).select(SPORTS)
        assertEquals(sports, sports.reconcile(rows, locations))

        val usb = sports.open(HubTab.Recordings, rows, locations).select(USB)
        assertEquals(usb, usb.reconcile(rows, locations))

        for (kept in listOf(HubSection.Timers, HubSection.Settings)) {
            val navigation = HubNavigation(section = kept)
            assertEquals(kept, navigation.reconcile(emptyList(), emptyList()).section)
        }
    }

    @Test
    fun aReloadFallsBackFromWhatIsGone() {
        val onlyFavourites = listOf(row(FAVOURITES, "Favourites"))
        val sports = HubNavigation().reconcile(rows, locations).select(SPORTS)
        assertEquals(
            HubSection.Live(FAVOURITES),
            sports.reconcile(onlyFavourites, locations).section
        )
        assertEquals(HubSection.Live(null), sports.reconcile(emptyList(), locations).section)

        val guide = sports.open(HubTab.Guide, rows, locations)
        assertEquals(
            HubSection.Guide(FAVOURITES),
            guide.reconcile(onlyFavourites, locations).section
        )
        assertEquals(HubSection.Live(null), guide.reconcile(emptyList(), locations).section)

        val usb = sports.open(HubTab.Recordings, rows, locations).select(USB)
        assertEquals(HubSection.Recordings(HDD), usb.reconcile(rows, listOf(HDD)).section)
        assertEquals(HubSection.Live(SPORTS), usb.reconcile(rows, emptyList()).section)
    }

    @Test
    fun onlyLiveTvSyncsItsBouquetAndOnlyRecordingsLoadsMovies() {
        val live = HubNavigation().reconcile(rows, locations)
        assertEquals(FAVOURITES, live.liveBouquetRef)
        assertNull(live.recordingsLocation)

        val guide = live.open(HubTab.Guide, rows, locations)
        assertNull(guide.liveBouquetRef)

        val recordings = live.open(HubTab.Recordings, rows, locations)
        assertNull(recordings.liveBouquetRef)
        assertEquals(HDD, recordings.recordingsLocation)
    }

    private fun row(reference: String, name: String) =
        HubBouquetRow(bouquet = Service(reference, name), services = emptyList())

    private companion object {
        const val FAVOURITES = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\""
        const val SPORTS = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\""
        const val HDD = "/media/hdd/movie/"
        const val USB = "/media/usb/movie/"
    }
}
