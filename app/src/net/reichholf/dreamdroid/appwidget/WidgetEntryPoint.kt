package net.reichholf.dreamdroid.appwidget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory

/**
 * Dependencies of the Glance [VirtualRemoteWidget] and [WidgetRemoteRequest]. Neither is built
 * by Hilt, so they look them up here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun profileRepository(): ProfileRepository

    fun enigmaClientFactory(): EnigmaClientFactory

    companion object {
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
    }
}
