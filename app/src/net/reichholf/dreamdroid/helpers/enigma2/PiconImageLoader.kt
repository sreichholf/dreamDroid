package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import net.reichholf.dreamdroid.DreamDroid
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
 */
object PiconImageLoader {
    fun install(context: Context) {
        val appContext = context.applicationContext
        SingletonImageLoader.setSafe {
            try {
                newImageLoader(appContext)
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

    fun newImageLoader(context: Context): ImageLoader {
        val okHttpClient = newOkHttpClient(context)
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .build()
    }

    @Suppress("UNUSED_PARAMETER")
    fun newOkHttpClient(context: Context): OkHttpClient {
        val trustAll = ProfileRepository.get().current.value?.allCertsTrusted == true
        return EnigmaOkHttp.client(EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS, trustAll)
            .newBuilder()
            .addInterceptor { chain ->
                val profile = ProfileRepository.get().current.value
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
