package net.reichholf.dreamdroid.room

import net.reichholf.dreamdroid.enigma.Timer
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerSnapshotMappingTest {
    @Test
    fun roundTripPreservesTimerFields() {
        val timer = Timer(
            reference = "1:0:1:6DCA:44C:1:C00000:0:0:0:",
            serviceName = "Das Erste HD",
            eit = "10",
            name = "News",
            description = "desc",
            descriptionExtended = "extended",
            disabled = "0",
            begin = "1476644933",
            end = "1476649083",
            duration = "4150",
            beginReadable = "16 Oct 2016 20:08",
            endReadable = "16 Oct 2016 21:18",
            durationReadable = "1:09",
            startPrepare = "1",
            justPlay = "0",
            afterEvent = "3",
            location = "HDD",
            tags = "news",
            logEntries = "log",
            fileName = "file.ts",
            backOff = "0",
            nextActivation = "1",
            firstTryPrepare = "0",
            state = "0",
            repeated = "0",
            dontSave = "0",
            canceled = "0",
            toggleDisabled = "0"
        )
        assertEquals(timer, timer.toListEntity(7, 2).toTimer())
        val entity = timer.toListEntity(7, 2)
        assertEquals(7, entity.profileId)
        assertEquals(2, entity.position)
    }
}
