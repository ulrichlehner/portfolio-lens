package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.portfoliolens.page.PageWriter;
import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.testing.SyntheticUniverse;

/** A generated portfolio of about 4,000 companies: page size and element budget. */
public class LargePortfolioTest
{
    private static final String TOKEN = "synthetic-pp-token";
    private static final Pattern ELEMENT = Pattern.compile("<[a-zA-Z]");

    @TempDir
    Path temp;

    static int elements(String html)
    {
        var matcher = ELEMENT.matcher(html);
        int count = 0;
        while (matcher.find())
            count++;
        return count;
    }

    @Test
    public void testPageOfFourThousandCompanies() throws Exception
    {
        var universe = new SyntheticUniverse(4000, 10, 20);
        try (var pp = new FakePpServer(TOKEN).serve("/v1/version", "pp/version.json").serve("/v1/files",
                        "pp/files-one.json"))
        {
            pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/holdings", 200, universe.holdingsJson());
            pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/instruments", 200, universe.instrumentsJson());
            var env = new HashMap<String, String>();
            env.put(LensConfig.PP_URL, pp.baseUrl().toString());
            env.put(LensConfig.PP_TOKEN, TOKEN);
            env.put(LensConfig.EULERPOOL_API_KEY, "synthetic-key");
            env.put(LensConfig.LOCAL_STORAGE_CONFIRMED, "true");
            env.put(LensConfig.REQUEST_BUDGET, "1000000");
            env.put(LensConfig.RUN_TIMEOUT, "PT5M");
            env.put(LensConfig.CACHE_DIR, temp.resolve("cache").toString());
            env.put(LensConfig.OUTPUT_DIR, temp.resolve("out").toString());
            var settings = LensConfig.load(env, Optional.empty(), temp, true).settings().orElseThrow();
            new LensRun(settings, new LensRun.Environment(universe,
                            Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC), () -> {
                            })).run();
        }
        var page = Files.readString(temp.resolve("out").resolve(PageWriter.PAGE));
        var bytes = page.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        var elements = elements(page);
        System.out.println("LARGE PORTFOLIO: bytes=" + bytes + " elements=" + elements + " requests="
                        + universe.providerRequests() + " " + universe.requestsByPath());
        var demo = System.getProperty("lens.demo");
        if (demo != null)
        {
            Files.createDirectories(Path.of(demo));
            Files.writeString(Path.of(demo).resolve("large.html"), page);
        }
        assertTrue(page.contains("Invented Company 1 Inc."));
    }
}
