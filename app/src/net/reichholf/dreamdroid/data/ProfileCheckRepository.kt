package net.reichholf.dreamdroid.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile

/**
 * Connection check for a profile that need not be saved or active, such as a setup
 * draft. An interface because the setup screen tests need a held check and a TLS
 * failure, which a server fixture cannot produce cheaply (decision 6 in
 * docs/hilt-migration.md).
 */
interface ProfileCheckRepository {
    /** Asks [profile]'s receiver again; a cached device-info answer is dropped first. */
    suspend fun check(profile: Profile): ProfileCheckResult

    /** Like [check], but a device-info answer cached for [profile] stands in for asking. */
    suspend fun checkReusingDeviceInfo(profile: Profile): ProfileCheckResult
}

/** [ProfileCheckRepository] over [CheckProfile]. */
@Singleton
class ReceiverProfileCheckRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileRepository
) : ProfileCheckRepository {
    override suspend fun check(profile: Profile): ProfileCheckResult {
        profiles.setDeviceInfo(profile, null)
        return checkReusingDeviceInfo(profile)
    }

    override suspend fun checkReusingDeviceInfo(profile: Profile): ProfileCheckResult =
        withContext(Dispatchers.IO) { CheckProfile.checkProfile(profile, context) }
}
