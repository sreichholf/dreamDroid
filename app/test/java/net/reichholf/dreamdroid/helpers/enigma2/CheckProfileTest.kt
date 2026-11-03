package net.reichholf.dreamdroid.helpers.enigma2

import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.receiverApis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckProfileTest {
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val clients = receiverApis(profiles, testProfiles.context, testProfiles.capabilities)

    @Test
    fun aNullHostIsIllegal() = runBlocking<Unit> {
        assertIllegalHost(Profile())
    }

    @Test
    fun aHostWithUserInfoIsIllegal() = runBlocking<Unit> {
        assertIllegalHost(
            Profile.getDefault().apply { host = "receiver.local@attacker.example" }
        )
    }

    @Test
    fun anIllegalStreamHostIsIllegal() = runBlocking<Unit> {
        assertIllegalHost(
            Profile.getDefault().apply {
                host = "10.0.0.1"
                streamHost = "stream.local/path"
            }
        )
    }

    private suspend fun assertIllegalHost(profile: Profile) {
        val result = CheckProfile.checkProfile(
            profile,
            clients.forProfile(profile),
            clients.detector(profile),
            profiles,
            testProfiles.capabilities
        )

        assertTrue(result.hasError)
        assertEquals(R.string.illegal_host, result.errorTextId)
        val failure = result.failure as EnigmaFailure.Unreachable
        assertEquals(EnigmaFailure.UnreachableReason.IllegalHost, failure.reason)
    }
}
