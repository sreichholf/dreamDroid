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
        previousProfile = DreamDroid.currentProfileOrNull()
        DreamDroid.setCurrentProfile(Profile().apply { host = "127.0.0.1" })
    }

    @After
    fun restoreProfile() {
        val previous = previousProfile
        if (previous != null) {
            DreamDroid.setCurrentProfile(previous)
        } else {
            DreamDroid.loadCurrentProfile(app())
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
