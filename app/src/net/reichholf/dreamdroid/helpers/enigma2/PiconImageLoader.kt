package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.map.Mapper
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.toUri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import okhttp3.Credentials
import okhttp3.OkHttpClient

/**
 * Process-wide Coil [ImageLoader] for Enigma2 picons.
 *
 * TLS matches [EnigmaOkHttp] for the current profile, including trust-all.
 * Basic auth is added on the first request from the current profile, not from URL userinfo.
 * An [OnlinePicon] becomes the current profile's `/file` URL.
 */
object PiconImageLoader {
    fun install(context: Context) {
        val appContext = context.applicationContext
        SingletonImageLoader.setSafe {
            try {
                val deps = EntryPointAccessors
                    .fromApplication(appContext, PiconImageLoaderEntryPoint::class.java)
                newImageLoader(appContext, deps.profileRepository(), deps.enigmaOkHttp())
            } catch (e: Exception) {
                e.printStackTrace()
                ImageLoader.Builder(appContext).build()
            }
        }
    }

    fun clearCache(context: Context) {
        install(context)
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    fun newImageLoader(
        context: Context,
        profiles: ProfileRepository,
        okHttp: EnigmaOkHttp
    ): ImageLoader {
        val okHttpClient = newOkHttpClient(profiles, okHttp)
        return ImageLoader.Builder(context)
            .components {
                add(onlinePiconMapper(profiles))
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .build()
    }

    /** [OnlinePicon] to the `/file` URL of the current profile; unmapped without a profile. */
    fun onlinePiconMapper(profiles: ProfileRepository): Mapper<OnlinePicon, Uri> =
        Mapper { data, _ ->
            profiles.current.value?.let { Picon.onlinePiconUrl(it, data.fileName).toUri() }
        }

    private fun newOkHttpClient(profiles: ProfileRepository, okHttp: EnigmaOkHttp): OkHttpClient {
        val trustAll = profiles.current.value?.allCertsTrusted == true
        return okHttp.client(EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS, trustAll)
            .newBuilder()
            .addInterceptor { chain ->
                val profile = profiles.current.value
                val request = if (profile?.login == true) {
                    val cred = Credentials.basic(profile.user.orEmpty(), profile.pass.orEmpty())
                    chain.request().newBuilder().header("Authorization", cred).build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }
}

/** What the picon [ImageLoader] needs; Coil builds it outside any Hilt component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PiconImageLoaderEntryPoint {
    fun profileRepository(): ProfileRepository

    fun enigmaOkHttp(): EnigmaOkHttp
}
