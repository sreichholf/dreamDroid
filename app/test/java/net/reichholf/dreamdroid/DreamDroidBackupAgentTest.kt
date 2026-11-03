package net.reichholf.dreamdroid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the entity keys of the 1.15 and 2.0.470 agents. A changed key leaves the old passwords
 * in the transport. `BackupDataOutput` has no public constructor and the JVM tests carry no
 * mocking library, so the agent writes through
 * [DreamDroidBackupAgent.deleteLegacyKeyValueEntities] and this checks that seam.
 */
class DreamDroidBackupAgentTest {
    @Test
    fun deletesTheKeyValueEntitiesOfTheOldAgent() {
        val writes = mutableListOf<Pair<String, Int>>()

        DreamDroidBackupAgent.deleteLegacyKeyValueEntities { key, dataSize ->
            writes += key to dataSize
        }

        assertEquals(
            listOf(
                "preferences:net.reichholf.dreamdroid_preferences" to -1,
                "database:../databases/dreambox" to -1,
                "database:../databases/dreamdroid" to -1
            ),
            writes
        )
    }
}
