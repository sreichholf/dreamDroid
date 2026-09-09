package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MovieParserTest {
    @Test
    public void parsesMovielistFixture() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/movielist.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<Movie> movies = MovieParser.INSTANCE.parse(xml);
        assertNotNull(movies);
        assertEquals(2, movies.size());

        Movie first = movies.get(0);
        assertEquals("Evening News", first.getTitle());
        assertEquals("Das Erste HD", first.getServiceName());
        assertEquals("45", first.getLength());
        assertEquals("news sports", first.getTags());
        assertEquals("/media/hdd/movie/news.ts", first.getFileName());
        assertEquals("104857600", first.getFileSize());
        assertEquals("100 MB", first.getFileSizeReadable());
        assertFalse(first.getTimeReadable().isEmpty());
        assertTrue(first.getDescriptionExtended().contains("Full bulletin."));

        Movie second = movies.get(1);
        assertEquals("Empty Size", second.getTitle());
        assertEquals("ZDF HD", second.getServiceName());
        assertEquals("None", second.getFileSize());
        assertEquals("0 MB", second.getFileSizeReadable());
    }

    @Test
    public void stripsControlCharactersFromServiceName() {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e2movielist><e2movie>"
                + "<e2servicereference>1:0:0:0:0:0:0:0:0:0:/f.ts</e2servicereference>"
                + "<e2title>T</e2title>"
                + "<e2description/>"
                + "<e2descriptionextended/>"
                + "<e2servicename>ZDF\u0001HD</e2servicename>"
                + "<e2time>1893456000</e2time>"
                + "<e2length>1</e2length>"
                + "<e2tags/>"
                + "<e2filename>/f.ts</e2filename>"
                + "<e2filesize>0</e2filesize>"
                + "</e2movie></e2movielist>";
        List<Movie> movies = MovieParser.INSTANCE.parse(xml);
        assertEquals(1, movies.size());
        assertEquals("ZDFHD", movies.get(0).getServiceName());
    }

    @Test
    public void emptyXmlYieldsNoMovies() {
        assertEquals(0, MovieParser.INSTANCE.parse("").size());
    }

    @Test
    public void malformedXmlYieldsNoMovies() {
        assertEquals(0, MovieParser.INSTANCE.parse("<e2movielist><e2movie>").size());
    }
}
