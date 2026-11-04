package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import io.portfoliolens.testing.RecordingHttp;

public class CountingHttpGetTest
{
    @Test
    public void testCountsByTypeAndOutcomeWithoutIdentifiers() throws Exception
    {
        var http = new CountingHttpGet(new RecordingHttp(uri -> switch (uri.getPath())
        {
            case "/api/1/equity/profile/XX0000000017" -> RecordingHttp.json(200, "{\"isin\":\"XX0000000017\"}");
            case "/api/1/equity/profile/XX0000000025" -> RecordingHttp.json(404, "");
            case "/api/1/equity/regions/XX0000000017" -> RecordingHttp.json(200, "[]");
            case "/api/1/etf/holdings/XX0000000033" -> RecordingHttp.json(500, "");
            case "/api/v1/lei-records" -> RecordingHttp.json(200, "{\"meta\":{\"pagination\":{\"total\":0}},\"data\":[]}");
            default -> RecordingHttp.json(200, "{\"count\":2,\"results\":[1,2]}");
        }));
        for (var path : new String[] { "https://api.eulerpool.com/api/1/equity/profile/XX0000000017",
                        "https://api.eulerpool.com/api/1/equity/profile/XX0000000025",
                        "https://api.eulerpool.com/api/1/equity/regions/XX0000000017",
                        "https://api.eulerpool.com/api/1/etf/holdings/XX0000000033",
                        "https://api.gleif.org/api/v1/lei-records?filter=x",
                        "https://api.eulerpool.com/api/1/equity/search?q=x" })
            http.get(URI.create(path), Map.of());
        var summary = http.summary();
        assertTrue(summary.contains("company profile 2 (found 1, not found 1, failed 0)"), summary);
        assertTrue(summary.contains("revenue split 1 (found 0, not found 1, failed 0)"), summary);
        assertTrue(summary.contains("fund composition 1 (found 0, not found 0, failed 1)"), summary);
        assertTrue(summary.contains("GLEIF register 1 (found 0, not found 1, failed 0)"), summary);
        assertTrue(summary.contains("classification search 1 (found 1"), summary);
        assertFalse(summary.contains("XX00"), summary);
        assertEquals(6, http.count());

        var failing = new CountingHttpGet((uri, headers) -> {
            throw new IOException("down");
        });
        assertThrows(IOException.class, () -> failing.get(URI.create("https://api.gleif.org/api/v1/lei-records"), Map.of()));
        assertTrue(failing.summary().contains("GLEIF register 1 (found 0, not found 0, failed 1)"));
    }

    @Test
    public void testLogLevelAppliesToLensOnly()
    {
        Main.configureLogging();
        // without LENS_LOG_LEVEL: INFO everywhere; the JDK's TLS logger never gets FINE
        assertFalse(Logger.getLogger("javax.net.ssl").isLoggable(Level.FINE));
        assertFalse(Logger.getLogger("jdk.internal.httpclient.debug").isLoggable(Level.FINE));
        assertTrue(Logger.getLogger("io.portfoliolens.job.LensRun").isLoggable(Level.INFO));

        Main.configureLogging("FINE");
        try
        {
            assertTrue(Logger.getLogger("io.portfoliolens.job.LensRun").isLoggable(Level.FINE));
            assertFalse(Logger.getLogger("javax.net.ssl").isLoggable(Level.FINE));
            assertFalse(Logger.getLogger("jdk.internal.httpclient.debug").isLoggable(Level.FINE));
        }
        finally
        {
            Main.configureLogging(null);
        }
    }
}
