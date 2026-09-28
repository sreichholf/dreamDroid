package net.reichholf.dreamdroid.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.data.MdnsReceiverDiscovery
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ProfileStore
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.data.RoomProfileStore

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    abstract fun profileStore(store: RoomProfileStore): ProfileStore

    @Binds
    abstract fun profileCheckRepository(
        repository: ReceiverProfileCheckRepository
    ): ProfileCheckRepository

    companion object {
        @Provides
        fun receiverDiscovery(): ReceiverDiscovery = MdnsReceiverDiscovery
    }
}
