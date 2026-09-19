package net.reichholf.dreamdroid.ui.epg

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EventListLoadResult
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgBouquetPickerTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pickerResultLoadsPickedBouquetNotDrawerDefault() = runBlocking {
        val session = EpgBouquetSession()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.listState = EpgBouquetListState()
        session.refresh = ComposeRefreshState()
        session.bouquetRef = DEFAULT
        session.bouquetName = "Favourites (TV)"
        session.timeSec = NOW
        session.profileId = PROFILE
        session.epgDao = db.epgDao()
        session.shouldSkipReceiverHttp = { false }
        var loadedBref: String? = null
        session.loadEvents = { _, params ->
            loadedBref = params.first { it.key == "bRef" }.value()
            EventListLoadResult(true, listOf(Event(title = "Picked News")), null)
        }
        val data = Intent().putExtra(KEY_BOUQUET, Service(PICKED, "Picked TV"))
        session.onActivityResult(Statics.REQUEST_PICK_BOUQUET, Activity.RESULT_OK, data)
        assertEquals(PICKED, session.bouquetRef)
        assertEquals("Picked TV", session.bouquetName)
        assertEquals(PICKED, EpgBouquetRestore.resolveRef(DEFAULT, session.bouquetRef))
        session.loadAndApply(forceRefresh = true)
        assertEquals(PICKED, loadedBref)
        assertEquals(listOf("Picked News"), session.listState!!.items.map { it.title })
    }

    companion object {
        private const val PROFILE = 7
        private const val NOW = 1_893_456_000
        private const val DEFAULT =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        private const val PICKED =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
    }
}
