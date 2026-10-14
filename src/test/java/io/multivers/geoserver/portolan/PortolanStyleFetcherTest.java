package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class PortolanStyleFetcherTest {
    @Test
    public void rejectsUnsafeStyleDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PortolanStyleFetcher.validateRemoteUri(URI.create("file:///tmp/style.json")));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortolanStyleFetcher.validateRemoteUri(URI.create("http://127.0.0.1/style.json")));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortolanStyleFetcher.validateRemoteUri(URI.create("http://169.254.169.254/latest/meta-data")));
    }

    @Test
    public void enforcesTheMaximumPayloadSize() {
        byte[] payload = "123456".getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PortolanStyleFetcher.readLimited(new ByteArrayInputStream(payload), 5));

        assertTrue(error.getMessage().contains("maximum size"));
    }

    @Test
    public void removesCredentialsAndQueryValuesFromDisplayedUrls() {
        assertEquals(
                "https://styles.example.test/roads.json",
                PortolanStyleFetcher.displayUri(
                        URI.create("https://user:secret@styles.example.test/roads.json?signature=secret")));
    }
}
