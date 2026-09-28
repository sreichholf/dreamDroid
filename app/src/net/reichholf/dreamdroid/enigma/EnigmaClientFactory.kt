package net.reichholf.dreamdroid.enigma

import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository

/**
 * Builds an [EnigmaClient] per operation. A client wraps one `EnigmaHttp`, which is bound
 * to one profile and cancels its in-flight call when a second fetch starts, so it must not
 * be shared across screens.
 */
@Singleton
class EnigmaClientFactory @Inject constructor(private val profiles: ProfileRepository) {
    /** A client for the active profile. */
    fun current(): EnigmaClient = EnigmaClient(profiles.requireCurrent())

    /** A client for [profile], which need not be the active one. */
    fun forProfile(profile: Profile): EnigmaClient = EnigmaClient(profile)
}
