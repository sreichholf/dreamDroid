package net.reichholf.dreamdroid.ui.timers

import net.reichholf.dreamdroid.enigma.Timer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimerEditBindTest {
    @Test
    fun firstBindRestoresSavedTagEvenWhenEpochDiffers() {
        val bind = timerEditBind(
            hasBound = false,
            boundTag = "",
            boundEpoch = 0,
            routeTag = "timer_edit:1:0:10",
            remountEpoch = 0,
            savedTag = "timer_edit:1:0:10"
        )
        assertEquals(TimerEditBind.RestoreSaved, bind)
    }

    @Test
    fun firstBindWithoutSavedTagLoadsLaunchTimer() {
        val bind = timerEditBind(
            hasBound = false,
            boundTag = "",
            boundEpoch = 0,
            routeTag = "timer_edit:new:10",
            remountEpoch = 0,
            savedTag = null
        )
        assertEquals(TimerEditBind.LoadLaunch, bind)
    }

    @Test
    fun differentSavedTagLoadsLaunchTimer() {
        val bind = timerEditBind(
            hasBound = false,
            boundTag = "",
            boundEpoch = 0,
            routeTag = "timer_edit:new:20",
            remountEpoch = 1,
            savedTag = "timer_edit:new:10"
        )
        assertEquals(TimerEditBind.LoadLaunch, bind)
    }

    @Test
    fun sameTagAndEpochKeepsSession() {
        val bind = timerEditBind(
            hasBound = true,
            boundTag = "timer_edit:1:0:10",
            boundEpoch = 2,
            routeTag = "timer_edit:1:0:10",
            remountEpoch = 2,
            savedTag = null
        )
        assertEquals(TimerEditBind.Keep, bind)
    }

    @Test
    fun remountEpochLoadsLaunchTimer() {
        val bind = timerEditBind(
            hasBound = true,
            boundTag = "timer_edit:1:0:10",
            boundEpoch = 2,
            routeTag = "timer_edit:1:0:10",
            remountEpoch = 3,
            savedTag = "timer_edit:1:0:10"
        )
        assertEquals(TimerEditBind.LoadLaunch, bind)
    }

    @Test
    fun adoptedEpochKeepsTimerFields() {
        val timer = Timer(name = "News", begin = "10", end = "20", repeated = "0")
        val session = TimerEditSession(
            routeTag = "timer_edit:1:0:10",
            remountEpoch = 2,
            timer = timer,
            timerOld = timer,
            isCreate = false,
            selectedTags = arrayListOf("hd"),
            checkedDays = booleanArrayOf(true, false, false, false, false, false, false)
        )
        assertSame(session, session.withRouteEpoch(2))
        val adopted = session.withRouteEpoch(0)
        assertEquals(0, adopted.remountEpoch)
        assertEquals("timer_edit:1:0:10", adopted.routeTag)
        assertEquals("News", adopted.timer.name)
        assertEquals("10", adopted.timer.begin)
        assertEquals(listOf("hd"), adopted.selectedTags)
        assertFalse(adopted.isCreate)
        assertTrue(adopted.checkedDays[0])
        assertEquals(2, session.remountEpoch)
    }
}
