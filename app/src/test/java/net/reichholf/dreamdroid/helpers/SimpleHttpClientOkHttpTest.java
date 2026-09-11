package net.reichholf.dreamdroid.helpers;

import net.reichholf.dreamdroid.Profile;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleHttpClientOkHttpTest {
    @Test
    public void buildUrl_httpHostAndQuery() {
        Profile profile = new Profile();
        profile.setHost("box.local");
        profile.setPort(80);
        profile.setSsl(false);
        profile.setLogin(false);
        SimpleHttpClient client = SimpleHttpClient.getInstance(profile);
        String url = client.buildUrl("/web/about", new ArrayList<>());
        assertEquals("http://box.local:80/web/about?", url);
    }

    @Test
    public void buildUrl_httpsWhenSsl() {
        Profile profile = new Profile();
        profile.setHost("box.local");
        profile.setPort(443);
        profile.setSsl(true);
        profile.setLogin(false);
        SimpleHttpClient client = SimpleHttpClient.getInstance(profile);
        String url = client.buildUrl("/web/about", new ArrayList<>());
        assertTrue(url.startsWith("https://"));
        assertTrue(url.contains("box.local:443/web/about"));
    }

    @Test
    public void buildAuthedUrl_embedsUserInfo() {
        Profile profile = new Profile();
        profile.setHost("box.local");
        profile.setPort(80);
        profile.setSsl(false);
        profile.setLogin(true);
        profile.setUser("root");
        profile.setPass("secret");
        SimpleHttpClient client = SimpleHttpClient.getInstance(profile);
        String url = client.buildAuthedUrl("/web/about", new ArrayList<>());
        assertTrue(url.contains("root:secret@box.local:80"));
    }
}
