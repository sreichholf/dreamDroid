package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.TimerParser
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerListMapperTest {
    @Test
    fun toExtendedHashMapCopiesTimerFields() {
        val timers = TimerParser.parse(loadWebFixture("timerlist.xml"))
        assertNotNull(timers)
        val timer = timers!![0]

        val map = TimerListMapper.toExtendedHashMap(timer)
        assertEquals(timer.reference, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_REFERENCE))
        assertEquals(timer.serviceName, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_SERVICE_NAME))
        assertEquals(timer.eit, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_EIT))
        assertEquals(timer.name, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_NAME))
        assertEquals(timer.description, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DESCRIPTION))
        assertEquals(
            timer.descriptionExtended,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DESCRIPTION_EXTENDED),
        )
        assertEquals(timer.disabled, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DISABLED))
        assertEquals(timer.begin, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_BEGIN))
        assertEquals(
            timer.beginReadable,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_BEGIN_READEABLE),
        )
        assertEquals(timer.end, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_END))
        assertEquals(timer.endReadable, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_END_READABLE))
        assertEquals(timer.duration, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DURATION))
        assertEquals(
            timer.durationReadable,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DURATION_READABLE),
        )
        assertEquals(timer.justPlay, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_JUST_PLAY))
        assertEquals(timer.afterEvent, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_AFTER_EVENT))
        assertEquals(timer.location, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_LOCATION))
        assertEquals(timer.state, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_STATE))
        assertEquals(timer.repeated, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_REPEATED))
    }
}
