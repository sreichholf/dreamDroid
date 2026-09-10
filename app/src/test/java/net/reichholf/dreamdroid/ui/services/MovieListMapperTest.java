package net.reichholf.dreamdroid.ui.services;

import net.reichholf.dreamdroid.enigma.Movie;
import net.reichholf.dreamdroid.enigma.MovieParser;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MovieListMapperTest {
    @Test
    public void mapsTypedMoviesToComposeItems() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/movielist.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<Movie> movies = MovieParser.INSTANCE.parse(xml);
        assertNotNull(movies);
        List<MovieListItem> items = MovieListMapperKt.movieListItemsFromMovies(movies);
        assertEquals(2, items.size());
        assertEquals("Evening News", items.get(0).getTitle());
        assertEquals("100 MB", items.get(0).getFileSize());
        assertEquals("Empty Size", items.get(1).getTitle());
        assertEquals("0 MB", items.get(1).getFileSize());
    }

    @Test
    public void toExtendedHashMapKeepsLegacyKeys() {
        Movie movie = new Movie(
                "ref",
                "Title",
                "desc",
                "ext",
                "Service",
                "100",
                "readable",
                "30",
                "tag",
                "/file.ts",
                "1024",
                "0 MB"
        );
        ExtendedHashMap map = MovieListMapperKt.movieToExtendedHashMap(movie);
        assertEquals("Title", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_TITLE));
        assertEquals("ref", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_REFERENCE));
        assertEquals("/file.ts", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_NAME));
        assertEquals("0 MB", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_SIZE_READABLE));
    }

    @Test
    public void fromExtendedHashMapRoundTripsFields() {
        Movie movie = new Movie(
                "ref",
                "Title",
                "desc",
                "ext",
                "Service",
                "100",
                "readable",
                "30",
                "tag",
                "/file.ts",
                "1024",
                "0 MB"
        );
        ExtendedHashMap map = MovieListMapperKt.movieToExtendedHashMap(movie);
        Movie back = MovieListMapperKt.movieFromExtendedHashMap(map);
        assertEquals(movie.getReference(), back.getReference());
        assertEquals(movie.getTitle(), back.getTitle());
        assertEquals(movie.getDescription(), back.getDescription());
        assertEquals(movie.getDescriptionExtended(), back.getDescriptionExtended());
        assertEquals(movie.getServiceName(), back.getServiceName());
        assertEquals(movie.getTime(), back.getTime());
        assertEquals(movie.getTimeReadable(), back.getTimeReadable());
        assertEquals(movie.getLength(), back.getLength());
        assertEquals(movie.getTags(), back.getTags());
        assertEquals(movie.getFileName(), back.getFileName());
        assertEquals(movie.getFileSize(), back.getFileSize());
        assertEquals(movie.getFileSizeReadable(), back.getFileSizeReadable());
    }

    @Test
    public void emptyTypedListYieldsNoItems() {
        assertEquals(0, MovieListMapperKt.movieListItemsFromMovies(Collections.emptyList()).size());
    }
}
