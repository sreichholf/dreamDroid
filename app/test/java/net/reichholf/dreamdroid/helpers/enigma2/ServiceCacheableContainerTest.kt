package net.reichholf.dreamdroid.helpers.enigma2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceCacheableContainerTest {
    private val favourites =
        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
    private val nestedUnderFavourites =
        "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
    private val aggregateTv =
        "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || " +
            "(type == 25) FROM BOUQUET \"bouquets.tv\" ORDER BY bouquet"
    private val providerTv =
        "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || " +
            "(type == 25) FROM PROVIDERS ORDER BY name"
    private val allServicesTv =
        "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || " +
            "(type == 25) ORDER BY name"
    private val excluded = setOf(aggregateTv, providerTv, allServicesTv)

    @Test
    fun favouritesUserBouquetTabIsCacheableAsTabRoot() {
        assertTrue(
            Service.isCacheableUserBouquetContainer(
                favourites,
                favourites,
                listOf(favourites),
                excluded
            )
        )
    }

    @Test
    fun nestedFolderUnderFavouritesIsCacheableWhenTabRootIsFavourites() {
        assertTrue(
            Service.isCacheableUserBouquetContainer(
                nestedUnderFavourites,
                favourites,
                listOf(favourites),
                excluded
            )
        )
    }

    @Test
    fun providerDedicatedRefAndFromProvidersAreNotCacheable() {
        val known = listOf(favourites, providerTv)
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                providerTv,
                providerTv,
                known,
                excluded
            )
        )
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                nestedUnderFavourites,
                providerTv,
                known,
                excluded
            )
        )
    }

    @Test
    fun allServicesRootAndNestedFromSatellitesAreNotCacheable() {
        val known = listOf(favourites, allServicesTv)
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                allServicesTv,
                allServicesTv,
                known,
                excluded
            )
        )
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                nestedUnderFavourites,
                allServicesTv,
                known,
                excluded
            )
        )
    }

    @Test
    fun aggregateBouquetsTvIndexIsNotACacheableContainer() {
        val known = listOf(favourites, aggregateTv)
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                aggregateTv,
                aggregateTv,
                known,
                excluded
            )
        )
    }

    @Test
    fun unknownTabStripFailsClosed() {
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                favourites,
                favourites,
                emptyList(),
                excluded
            )
        )
    }

    @Test
    fun rosterRowKindSplitsChannelDirectoryMarker() {
        assertEquals(
            Service.ROSTER_KIND_CHANNEL,
            Service.rosterRowKind("1:0:1:6DCA:44C:1:C00000:0:0:0:")
        )
        assertEquals(
            Service.ROSTER_KIND_DIRECTORY,
            Service.rosterRowKind("1:1:1:0:0:0:0:0:0:0:")
        )
        assertEquals(
            Service.ROSTER_KIND_MARKER,
            Service.rosterRowKind("1:64:1:0:0:0:0:0:0:0:")
        )
    }
}
