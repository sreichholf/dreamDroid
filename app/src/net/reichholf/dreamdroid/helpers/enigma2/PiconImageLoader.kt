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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Process-wide Coil [ImageLoader] for Enigma2 picons.
 *
 * Each request uses the [EnigmaOkHttp] TLS setup of the current profile, including
 * trust-all, and basic auth from the current profile, not from URL userinfo.
 * A [PiconKey] becomes the current profile's `/file` URL or the synced file.
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
                // No Hilt component (a plain Compose test): synced picons only.
                ImageLoader.Builder(appContext)
                    .components { add(piconMapper(appContext, MutableStateFlow(null))) }
                    .build()
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
        val calls = PiconCalls(profiles, okHttp)
        return ImageLoader.Builder(context)
            .components {
                add(piconMapper(context, profiles.current))
                add(OkHttpNetworkFetcherFactory(callFactory = { calls }))
            }
            .build()
    }

    /**
     * [PiconKey] to the [current] profile's picon: its receiver's `/file` URL for online
     * picons, else the synced file. Coil keys its memory cache on the mapped URI, so profiles
     * do not share entries.
     */
    fun piconMapper(context: Context, current: StateFlow<Profile?>): Mapper<PiconKey, Uri> =
        Mapper { data, _ -> Picon.piconUri(context, current.value, data)?.toUri() }
}

/**
 * Picon calls on the OkHttp client for the current profile's trust-all setting, so a
 * profile switch changes TLS without rebuilding the process-wide [ImageLoader].
 */
internal class PiconCalls(
    private val profiles: ProfileRepository,
    private val okHttp: EnigmaOkHttp
) : Call.Factory {
    private val clients = ConcurrentHashMap<Boolean, OkHttpClient>()

    override fun newCall(request: Request): Call = client().newCall(request)

    fun client(): OkHttpClient {
        val trustAll = profiles.current.value?.allCertsTrusted == true
        return clients.getOrPut(trustAll) {
            okHttp.client(EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS, trustAll)
                .newBuilder()
                .addInterceptor { chain ->
                    val profile = profiles.current.value
                    val request = if (profile?.login == true) {
                        val cred = Credentials.basic(
                            profile.user.orEmpty(),
                            profile.pass.orEmpty()
                        )
                        chain.request().newBuilder().header("Authorization", cred).build()
                    } else {
                        chain.request()
                    }
                    chain.proceed(request)
                }
                .build()
        }
    }
}

/** What the picon [ImageLoader] needs; Coil builds it outside any Hilt component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PiconImageLoaderEntryPoint {
    fun profileRepository(): ProfileRepository

    fun enigmaOkHttp(): EnigmaOkHttp
}
