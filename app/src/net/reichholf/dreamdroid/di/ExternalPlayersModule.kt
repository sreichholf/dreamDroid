package net.reichholf.dreamdroid.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.data.ExternalPlayers
import net.reichholf.dreamdroid.data.PackageManagerExternalPlayers

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalPlayersModule {
    @Binds
    abstract fun externalPlayers(players: PackageManagerExternalPlayers): ExternalPlayers
}
