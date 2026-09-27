package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.reflect.KClass
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.ui.multiepg.MULTI_EPG_VISIBLE_MINUTES_KEY
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TvMultiEpgViewModelTest {
    private var previousProfile: Profile? = null

    @Before
    fun installProfile() {
        previousProfile = ProfileRepository.get().current.value
        ProfileRepository.get().setCurrent(Profile().apply { host = "127.0.0.1" })
    }

    @After
    fun restoreProfile() {
        val previous = previousProfile
        if (previous != null) {
            ProfileRepository.get().setCurrent(previous)
        } else {
            ProfileRepository.get().loadCurrent(app())
        }
    }

    @Test
    fun visibleMinutesRestoreFromSavedStateHandle() {
        val handle = SavedStateHandle(mapOf(MULTI_EPG_VISIBLE_MINUTES_KEY to 240))
        withViewModel(handle) { viewModel ->
            assertEquals(240, viewModel.visibleMinutes)
            viewModel.onVisibleMinutesChange(60)
            assertEquals(60, handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY))
        }
        withViewModel(SavedStateHandle()) { viewModel ->
            assertEquals(MultiEpgZoom.DEFAULT_MINUTES, viewModel.visibleMinutes)
        }
    }

    @Test
    fun overlayStateStaysOnTheViewModel() {
        val event = Event(eventId = "42", title = "News")
        withViewModel(SavedStateHandle()) { viewModel ->
            assertEquals(null, viewModel.detailEvent)
            assertEquals(null, viewModel.editTimerEvent)
            assertEquals(false, viewModel.pickingBouquet)
            viewModel.showDetail(event)
            assertEquals(event, viewModel.detailEvent)
            viewModel.dismissDetail()
            viewModel.showTimerEditor(event)
            assertEquals(null, viewModel.detailEvent)
            assertEquals(event, viewModel.editTimerEvent)
            viewModel.dismissTimerEditor()
            assertEquals(null, viewModel.editTimerEvent)
            viewModel.showBouquetPicker()
            assertEquals(true, viewModel.pickingBouquet)
            viewModel.pickBouquet(Service(viewModel.bouquetRef, "Same"))
            assertEquals(false, viewModel.pickingBouquet)
        }
    }

    private fun app(): Application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as Application

    private fun withViewModel(handle: SavedStateHandle, block: (TvMultiEpgViewModel) -> Unit) {
        val app = app()
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                TvMultiEpgViewModel(app, handle) as T
        }
        try {
            block(ViewModelProvider(store, factory)[TvMultiEpgViewModel::class.java])
        } finally {
            store.clear()
        }
    }
}
