package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import okhttp3.OkHttpClient

/**
 * Process-wide Coil [ImageLoader] for Enigma2 picons.
 *
 * Shares the [EnigmaOkHttp] connection pool and dispatcher. Auth is an
 * Authorization interceptor (plus a URL-userinfo authenticator fallback).
 * Trust-all uses the same [net.reichholf.dreamdroid.ssl.DreamDroidTrustManager]
 * instance as EnigmaHttp. Re-[install] with replace=true when the current
 * profile's login/ssl/trust-all changes; [SingletonImageLoader.setSafe]
 * will not replace an already-created loader.
 */
object PiconImageLoader {
    @OptIn(DelicateCoilApi::class)
    fun install(context: Context, replace: Boolean = false) {
        val appContext = context.applicationContext
        val factory = SingletonImageLoader.Factory {
            try {
                newImageLoader(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
                ImageLoader.Builder(appContext).build()
            }
        }
        if (replace) {
            SingletonImageLoader.setUnsafe(factory)
        } else {
            SingletonImageLoader.setSafe(factory)
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
        val trustAll = DreamDroid.currentProfileOrNull()?.allCertsTrusted == true
        return EnigmaOkHttp.piconClient(
            EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
            trustAll
        )
    }
}
