package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceDirectoryTest {
    private val favourites =
        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
    private val nestedUnderFavourites =
        "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
    private val fromProviders =
        "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || " +
            "(type == 25) FROM PROVIDERS ORDER BY name"

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun excluded(): Set<String> {
        val tv = context().resources.getStringArray(R.array.servicerefstv)
        val radio = context().resources.getStringArray(R.array.servicerefsradio)
        return tv.toSet() + radio.toSet()
    }

    private fun aggregateTv(): String = context().resources.getStringArray(R.array.servicerefstv)[0]

    private fun providerTv(): String = context().resources.getStringArray(R.array.servicerefstv)[1]

    private fun allServicesTv(): String =
        context().resources.getStringArray(R.array.servicerefstv)[2]

    @Test
    fun directoryFlagBitIsDirectory() {
        assertTrue(Service.isDirectory("1:1:1:0:0:0:0:0:0:0:"))
    }

    @Test
    fun providersPathWithoutFlagIsDirectory() {
        assertTrue(Service.isDirectory(fromProviders))
    }

    @Test
    fun bouquetPathWithoutFlagIsDirectory() {
        assertTrue(Service.isDirectory(favourites))
    }

    @Test
    fun plainServiceIsNotDirectory() {
        assertFalse(Service.isDirectory("1:0:1:6DCA:44C:1:C00000:0:0:0:"))
    }

    @Test
    fun nullOrEmptyIsNotDirectory() {
        assertFalse(Service.isDirectory(null))
        assertFalse(Service.isDirectory(""))
    }

    @Test
    fun favouritesUserBouquetTabIsCacheableAsTabRoot() {
        assertTrue(
            Service.isCacheableUserBouquetContainer(
                favourites,
                favourites,
                listOf(favourites),
                excluded()
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
                excluded()
            )
        )
    }

    @Test
    fun providerDedicatedRefAndFromProvidersAreNotCacheable() {
        val known = listOf(favourites, providerTv())
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                providerTv(),
                providerTv(),
                known,
                excluded()
            )
        )
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                fromProviders,
                fromProviders,
                known,
                excluded()
            )
        )
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                nestedUnderFavourites,
                providerTv(),
                known,
                excluded()
            )
        )
    }

    @Test
    fun allServicesRootAndNestedFolderAreNotCacheable() {
        val known = listOf(favourites, allServicesTv())
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                allServicesTv(),
                allServicesTv(),
                known,
                excluded()
            )
        )
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                nestedUnderFavourites,
                allServicesTv(),
                known,
                excluded()
            )
        )
    }

    @Test
    fun aggregateBouquetsTvIndexIsNotACacheableContainer() {
        val aggregate = aggregateTv()
        val known = listOf(favourites, aggregate)
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                aggregate,
                aggregate,
                known,
                excluded()
            )
        )
        assertFalse(
            Service.isCacheableUserBouquetContainer(
                favourites,
                aggregate,
                known,
                excluded()
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
                excluded()
            )
        )
    }
}
