package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieParserTest {
    @Test
    fun parsesMovielistFixture() {
        val movies = MovieParser.parse(loadWebFixture("movielist.xml"))
        assertNotNull(movies)
        assertEquals(2, movies!!.size)

        val first = movies[0]
        assertEquals("Evening News", first.title)
        assertEquals("Das Erste HD", first.serviceName)
        assertEquals("45", first.length)
        assertEquals("news sports", first.tags)
        assertEquals("/media/hdd/movie/news.ts", first.fileName)
        assertEquals("104857600", first.fileSize)
        assertEquals("100 MB", first.fileSizeReadable)
        assertFalse(first.timeReadable.isEmpty())
        assertTrue(first.descriptionExtended.contains("Full bulletin."))

        val second = movies[1]
        assertEquals("Empty Size", second.title)
        assertEquals("ZDF HD", second.serviceName)
        assertEquals("None", second.fileSize)
        assertEquals("0 MB", second.fileSizeReadable)
    }

    @Test
    fun stripsControlCharactersFromServiceName() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2movielist><e2movie>
            <e2servicereference>1:0:0:0:0:0:0:0:0:0:/f.ts</e2servicereference>
            <e2title>T</e2title>
            <e2description/>
            <e2descriptionextended/>
            <e2servicename>ZDF\u0001HD</e2servicename>
            <e2time>1893456000</e2time>
            <e2length>1</e2length>
            <e2tags/>
            <e2filename>/f.ts</e2filename>
            <e2filesize>0</e2filesize>
            </e2movie></e2movielist>
        """.trimIndent().replace("\\u0001", "\u0001")
        val movies = MovieParser.parse(xml)
        assertNotNull(movies)
        assertEquals(1, movies!!.size)
        assertEquals("ZDFHD", movies[0].serviceName)
    }

    @Test
    fun emptyXmlYieldsNull() {
        assertNull(MovieParser.parse(""))
    }

    @Test
    fun malformedXmlYieldsNull() {
        assertNull(MovieParser.parse("<e2movielist><e2movie>"))
    }
}
