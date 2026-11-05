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
        return render(Map.of());
    }

    private String render(Map<String, String> extra) throws Exception
    {
        try (var pp = new FakePpServer(TOKEN).serveStandard())
        {
            var holdings = Fixtures.read("pp/holdings.json").replace("Synthetic German Share", HOSTILE.replace("\"", "\\\""));
            var instruments = Fixtures.read("pp/instruments.json").replace("Synthetic German Share",
                            HOSTILE.replace("\"", "\\\""));
            pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/holdings", 200, holdings);
            pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/instruments", 200, instruments);
            var env = new java.util.HashMap<>(Map.of(LensConfig.PP_URL, pp.baseUrl().toString(), LensConfig.PP_TOKEN, TOKEN,
                            LensConfig.EULERPOOL_API_KEY, KEY, LensConfig.LOCAL_STORAGE_CONFIRMED, "true",
                            LensConfig.CACHE_DIR, temp.resolve("cache").toString(), LensConfig.OUTPUT_DIR,
                            temp.resolve("out").toString()));
            env.putAll(extra);
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
        // tile colour legend: green held directly to blue through funds, swatches under the bars
        assertTrue(page.contains("<span class=\"legend-caption\">Tile color:</span> Held directly <span class=\"gradient\""
                        + " style=\"background:linear-gradient(to right,#2CA02C,#1F77B4)\"></span> Through funds"));
        assertTrue(page.contains("<span class=\"legend-caption\">Bar color:</span>"));
        assertTrue(page.contains("class=\"tm-tile\" fill=\"#"));
        var overlap = page.substring(page.indexOf("<section class=\"overlap\">"),
                        page.indexOf("</section>", page.indexOf("<section class=\"overlap\">")));
        assertFalse(overlap.contains("chart-legend"), "no legend for the overlap matrix");
        // same-coloured tiles are separated by a stroke
        assertTrue(page.contains("stroke:var(--bg);stroke-width:1;vector-effect:non-scaling-stroke"));
        assertTrue(page.contains("<select id=\"grouping\" disabled>"));

        // generation time, staleness slot, data status collapsed on load, dark palette
        assertTrue(page.contains("<time id=\"generated\" datetime=\"2026-07-20T08:00:00Z\">"));
        assertTrue(page.contains("id=\"stale\" class=\"stale\" hidden"));
        assertTrue(page.contains("<details id=\"data-status\">"));
        assertTrue(page.contains("@media (prefers-color-scheme:dark)"));
        assertFalse(page.contains("Market classification"));

        // the inline script parses (Node.js runs in the dev image)
        var script = page.substring(page.lastIndexOf("<script>") + "<script>".length(), page.lastIndexOf("</script>"));
        var file = java.nio.file.Files.writeString(temp.resolve("page.js"), script);
        try
        {
            var check = new ProcessBuilder("node", "--check", file.toString()).redirectErrorStream(true).start();
            var output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, check.waitFor(), output);
        }
        catch (java.io.IOException e)
        {
            org.junit.jupiter.api.Assumptions.abort("Node.js not available: " + e.getMessage());
        }
    }

    @Test
    public void testAppLinkInTheHeader() throws Exception
    {
        var page = render(Map.of(LensConfig.APP_LINK_URL, "https://example.org/?a=1&b=2", LensConfig.APP_LINK_LABEL,
                        "PP & Co <test>"));
        var link = "<p class=\"app-link\"><a href=\"https://example.org/?a=1&amp;b=2\" rel=\"noopener\">"
                        + "PP &amp; Co &lt;test&gt;</a></p>";
        assertTrue(page.contains(link), page.substring(0, Math.min(page.length(), 1500)));
        var header = page.substring(page.indexOf("<header"), page.indexOf("</header>"));
        assertTrue(header.contains(link));
        assertFalse(header.contains("target="));
        assertFalse(page.contains("PP & Co <test>"));
        assertEquals(1, page.split("app-link\"").length - 1);
        assertEquals(2, page.split("<script").length - 1, "the link adds no script");
        assertFalse(page.contains("prefetch") || page.contains("<link "));
    }

    @Test
    public void testNoAppLinkWithoutSetting() throws Exception
    {
        var page = render();
        assertFalse(page.contains("<p class=\"app-link\">"));
        assertTrue(page.contains("</time></p><p id=\"stale\""), "the header is unchanged");
    }
}
