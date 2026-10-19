package net.reichholf.dreamdroid.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.intents.IntentFactory

/** An installed app that plays http video streams. */
data class ExternalPlayer(val packageName: String, val label: String)

/** The apps the external player setting offers. */
interface ExternalPlayers {
    suspend fun installed(): List<ExternalPlayer>
}

@Singleton
class PackageManagerExternalPlayers @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ExternalPlayers {
    override suspend fun installed(): List<ExternalPlayer> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        query(pm)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { ExternalPlayer(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun query(pm: PackageManager): List<ResolveInfo> {
        val intent = IntentFactory.externalPlayerQuery()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }
}
