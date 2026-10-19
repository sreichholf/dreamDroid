package net.reichholf.dreamdroid.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.video.Media3Formats
import net.reichholf.dreamdroid.video.MediaCodecMedia3Formats

@Module
@InstallIn(SingletonComponent::class)
abstract class Media3FormatsModule {
    @Binds
    abstract fun media3Formats(formats: MediaCodecMedia3Formats): Media3Formats
}
