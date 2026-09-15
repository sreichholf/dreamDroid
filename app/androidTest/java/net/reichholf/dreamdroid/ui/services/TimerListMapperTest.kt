package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.enigma.TimerParser
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerListMapperTest {
    @Test
    fun mapsTypedTimersToComposeItems() {
        val timers = TimerParser.parse(loadWebFixture("timerlist.xml"))
        assertNotNull(timers)
        val timer = timers!![0]
        assertEquals("1:0:19:EF74:3F9:1:C00000:0:0:0:", timer.reference)
        assertEquals("SAT.1 HD", timer.serviceName)
        assertEquals("39350", timer.eit)
        assertEquals("Navy CIS: L.A.", timer.name)
        assertEquals("Kein Rauch ohne Feuer", timer.description)
        assertEquals("Ein Feuerwehrmann stirbt bei einem Brand.", timer.descriptionExtended)
        assertEquals("0", timer.disabled)
        assertEquals("1476644933", timer.begin)
        assertEquals("1476649083", timer.end)
        assertEquals("4150", timer.duration)
        assertEquals("0", timer.justPlay)
        assertEquals("3", timer.afterEvent)
        assertEquals("None", timer.location)
        assertEquals("0", timer.state)
        assertEquals("0", timer.repeated)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val items = timerListItemsFrom(context, timers)
        assertEquals(2, items.size)
        assertEquals("Navy CIS: L.A.", items[0].name)
        assertEquals("SAT.1 HD", items[0].serviceName)
        assertEquals("Tagesschau", items[1].name)
        assertEquals("Das Erste HD", items[1].serviceName)
    }
}
