package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.portfoliolens.page.PageWriter;
import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.testing.Fixtures;
import io.portfoliolens.testing.SyntheticProviders;

/** Renders a page from synthetic data and checks its structure (breakdown-view spec). */
public class PageSmokeTest
{
    private static final String TOKEN = "synthetic-pp-token";
    private static final String KEY = "synthetic-provider-key";
    private static final String HOSTILE = "Synthetic <script>alert(1)</script> & \"Co\"";

    @TempDir
    Path temp;

    private String render() throws Exception
    {
        try (var pp = new FakePpServer(TOKEN).serveStandard())
        {
            var holdings = Fixtures.read("pp/holdings.json").replace("Synthetic German Share", HOSTILE.replace("\"", "\\\""));
            var instruments = Fixtures.read("pp/instruments.json").replace("Synthetic German Share",
                            HOSTILE.replace("\"", "\\\""));
            pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/holdings", 200, holdings);
            pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/instruments", 200, instruments);
            var env = Map.of(LensConfig.PP_URL, pp.baseUrl().toString(), LensConfig.PP_TOKEN, TOKEN,
                            LensConfig.EULERPOOL_API_KEY, KEY, LensConfig.LOCAL_STORAGE_CONFIRMED, "true",
                            LensConfig.CACHE_DIR, temp.resolve("cache").toString(), LensConfig.OUTPUT_DIR,
                            temp.resolve("out").toString());
            var settings = LensConfig.load(env, Optional.empty(), temp, true).settings().orElseThrow();
            new LensRun(settings, new LensRun.Environment(new SyntheticProviders(),
                            Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC), () -> {
                            })).run();
        }
        var page = temp.resolve("out").resolve(PageWriter.PAGE);
        // -Dlens.demo=<directory> keeps the synthetic page for a look in a browser
        var demo = System.getProperty("lens.demo");
        if (demo != null)
        {
            Files.createDirectories(Path.of(demo));
            Files.copy(page, Path.of(demo).resolve(PageWriter.PAGE), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return Files.readString(page);
    }

    @Test
    public void testPageStructure() throws Exception
    {
        var page = render();

        // every SVG is well-formed XML
        var svg = Pattern.compile("<svg[\\s\\S]*?</svg>").matcher(page);
        int count = 0;
        var factory = DocumentBuilderFactory.newInstance();
        while (svg.find())
        {
            count++;
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                            svg.group().replace("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"")
                                            .getBytes(StandardCharsets.UTF_8)));
        }
        assertTrue(count >= 14, "coverage, two charts per grouping and the overlap matrix: " + count);

        // values are escaped
        assertFalse(page.contains("<script>alert(1)</script>"));
        assertTrue(page.contains("Synthetic &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Co&quot;"));

        // nothing is loaded from the network
        assertFalse(Pattern.compile("(src|href)=\"(https?:)?//").matcher(page).find());
        assertFalse(page.contains("@import") || page.contains("url("));
        assertEquals(2, page.split("<script").length - 1, "the data block and the inline script");

        // no secrets or paths
        assertFalse(page.contains(TOKEN));
        assertFalse(page.contains(KEY));
        assertFalse(page.contains(temp.toString()));
        assertFalse(page.contains("/synthetic/path"));

        // without scripts the Company grouping is visible and the others hidden
        assertTrue(page.contains("id=\"view-COMPANY\" data-grouping=\"COMPANY\" data-title=\"Breakdown by Company\">"));
        assertTrue(page.contains("id=\"view-SECTOR\" data-grouping=\"SECTOR\" data-title=\"Breakdown by Sector\" hidden>"));
        // the heading follows the grouping: the script takes it from the embedded data
        assertTrue(page.contains("<h1 id=\"heading\">Breakdown by Company</h1>"));
        assertTrue(page.contains("\"COUNTRY\":\"Breakdown by Country (headquarters)\""));
        // same-coloured tiles are separated by a stroke
        assertTrue(page.contains("stroke:var(--bg);stroke-width:1;vector-effect:non-scaling-stroke"));
        assertTrue(page.contains("<select id=\"grouping\" disabled>"));

        // generation time, staleness slot, data status collapsed on load, dark palette
        assertTrue(page.contains("<time id=\"generated\" datetime=\"2026-07-20T08:00:00Z\">"));
        assertTrue(page.contains("id=\"stale\" class=\"stale\" hidden"));
        assertTrue(page.contains("<details id=\"data-status\">"));
        assertTrue(page.contains("@media (prefers-color-scheme:dark)"));
        assertFalse(page.contains("Market classification"));
    }
}
