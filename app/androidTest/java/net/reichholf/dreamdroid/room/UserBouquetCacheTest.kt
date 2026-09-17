package net.reichholf.dreamdroid.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserBouquetCacheTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RosterDao
    private lateinit var excluded: Set<String>

    private val favourites = Service(
        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
        "Favourites (TV)"
    )
    private val sports = Service(
        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet",
        "Sports"
    )
    private val nestedFolder = ServiceNowNext(
        serviceReference = "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite",
        serviceName = "Satellites"
    )
    private val channel = ServiceNowNext(
        serviceReference = "1:0:1:6DCA:44C:1:C00000:0:0:0:",
        serviceName = "Das Erste HD"
    )
    private val marker = ServiceNowNext(
        serviceReference = "1:64:1:0:0:0:0:0:0:0:",
        serviceName = "--------"
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        dao = db.rosterDao()
        excluded = UserBouquetCache.excludedHubTabRefs(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun provider(): Service {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ref = context.resources.getStringArray(R.array.servicerefstv)[1]
        return Service(ref, "Provider")
    }

    private fun allServices(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.resources.getStringArray(R.array.servicerefstv)[2]
    }

    private fun aggregate(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.resources.getStringArray(R.array.servicerefstv)[0]
    }

    @Test
    fun tabStripWriteDropsProviderAndReadsFavourites() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites, sports, provider()),
            excluded
        )
        val tabs = UserBouquetCache.loadTabStripServices(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV
        )
        assertEquals(listOf(favourites, sports), tabs)
        assertFalse(tabs.any { it.reference == provider().reference })
    }

    @Test
    fun favouritesTabRosterWriteAndRead() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        val wrote = UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            favourites.reference,
            favourites.reference,
            listOf(channel, nestedFolder, marker),
            excluded
        )
        assertTrue(wrote)
        val loaded = UserBouquetCache.loadRosterNowNext(
            dao,
            PROFILE,
            favourites.reference
        )!!
        assertEquals(3, loaded.size)
        assertEquals(channel.serviceName, loaded[0].serviceName)
        val stored = dao.getRoster(PROFILE, favourites.reference)
        assertEquals(EnigmaService.ROSTER_KIND_CHANNEL, stored[0].kind)
        assertEquals(EnigmaService.ROSTER_KIND_DIRECTORY, stored[1].kind)
        assertEquals(EnigmaService.ROSTER_KIND_MARKER, stored[2].kind)
    }

    @Test
    fun nestedFolderUnderFavouritesIsInserted() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        val wrote = UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            nestedFolder.serviceReference,
            favourites.reference,
            listOf(channel),
            excluded
        )
        assertTrue(wrote)
        val loaded = UserBouquetCache.loadRosterNowNext(
            dao,
            PROFILE,
            nestedFolder.serviceReference
        )
        assertEquals(listOf(channel.serviceName), loaded?.map { it.serviceName })
    }

    @Test
    fun providerIsNeverInsertedAsRoster() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites, provider()),
            excluded
        )
        val providerRef = provider().reference
        val wrote = UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            providerRef,
            providerRef,
            listOf(channel),
            excluded
        )
        assertFalse(wrote)
        assertNull(UserBouquetCache.loadRosterNowNext(dao, PROFILE, providerRef))
        assertEquals(0, dao.rosterContainerCount(PROFILE, providerRef))
    }

    @Test
    fun nestedAllServicesFolderIsNotInserted() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        val wrote = UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            nestedFolder.serviceReference,
            allServices(),
            listOf(channel),
            excluded
        )
        assertFalse(wrote)
        assertNull(
            UserBouquetCache.loadRosterNowNext(
                dao,
                PROFILE,
                nestedFolder.serviceReference
            )
        )
    }

    @Test
    fun aggregateBouquetsTvRootIsNotInsertedAsRosterContainer() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        val wrote = UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            aggregate(),
            aggregate(),
            listOf(favourites).map {
                ServiceNowNext(it.reference, it.name)
            },
            excluded
        )
        assertFalse(wrote)
        assertEquals(0, dao.rosterContainerCount(PROFILE, aggregate()))
        assertTrue(
            dao.getTabStrip(PROFILE, UserBouquetCache.KIND_TV)
                .none { it.serviceRef == aggregate() }
        )
    }

    @Test
    fun writtenEmptyRosterIsEmptyListNotMissing() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        val wrote = UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            favourites.reference,
            favourites.reference,
            emptyList(),
            excluded
        )
        assertTrue(wrote)
        val loaded = UserBouquetCache.loadRosterNowNext(
            dao,
            PROFILE,
            favourites.reference
        )
        assertEquals(emptyList<ServiceNowNext>(), loaded)
        assertNull(
            UserBouquetCache.loadRosterNowNext(dao, PROFILE, nestedFolder.serviceReference)
        )
    }

    @Test
    fun tabStripMakesHasUseDrivenCacheTrue() = runBlocking {
        assertFalse(hasUseDrivenCache(dao.getTabStripRefs(PROFILE)))
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites, sports),
            excluded
        )
        assertTrue(hasUseDrivenCache(dao.getTabStripRefs(PROFILE)))
    }

    companion object {
        private const val PROFILE = 7
    }
}
