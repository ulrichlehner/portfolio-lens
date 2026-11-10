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
        assertTrue(count >= 14, "coverage bar and two charts per grouping (7 groupings), plus the overlap matrix: " + count);

        // values are escaped
        assertFalse(page.contains("<script>alert(1)</script>"));
        assertTrue(page.contains("Synthetic &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Co&quot;"));

        // nothing is loaded from the network
        assertFalse(Pattern.compile("(src|href)=\"(https?:)?//").matcher(page).find());
        assertFalse(page.contains("@import") || page.contains("url("));
        assertEquals(3, page.split("<script").length - 1, "the js class script, the data block and the main script");

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
        var overlapStart = page.indexOf("<figure class=\"chart\" data-chart=\"overlap\"");
        var overlap = page.substring(overlapStart, page.indexOf("</figure>", overlapStart));
        assertFalse(overlap.contains("legend-caption"), "no colour legend for the overlap matrix");
        assertFalse(overlap.substring(0, overlap.indexOf('>')).contains("hidden"), "the overlap figure is never hidden");
        // same-coloured tiles are separated by a stroke
        assertTrue(page.contains("stroke:var(--bg);stroke-width:1;vector-effect:non-scaling-stroke"));
        assertTrue(page.contains("<select id=\"grouping\" disabled>"));

        // generation time, staleness slot, data status collapsed on load, dark palette
        assertTrue(page.contains("<time id=\"generated\" datetime=\"2026-07-20T08:00:00Z\">"));
        assertTrue(page.contains("id=\"stale\" class=\"stale\" hidden"));
        assertTrue(page.contains("<details id=\"data-status\">"));
        assertTrue(page.contains("@media (prefers-color-scheme:dark)"));
        assertFalse(page.contains("Market classification"));

        assertStructure(page);

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
        assertEquals(3, page.split("<script").length - 1, "the link adds no script");
        assertFalse(page.contains("prefetch") || page.contains("<link "));
    }

    @Test
    public void testNoAppLinkWithoutSetting() throws Exception
    {
        var page = render();
        assertFalse(page.contains("<p class=\"app-link\">"));
        var header = page.substring(page.indexOf("<header"), page.indexOf("</header>"));
        assertFalse(header.contains("app-link"), "no link and no placeholder in the header");
        assertTrue(Pattern.compile("<input type=\"search\"[^>]*>\\s*</div>").matcher(header).find(),
                        "the search input is directly followed by the end of the bar line");
    }

    private static int occurrences(String text, String part)
    {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length()))
            count++;
        return count;
    }

    private static final String[] GROUPINGS = { "COMPANY", "COUNTRY", "REGION", "SECTOR", "INDUSTRY", "CURRENCY",
                    "REVENUE_REGION" };

    private static void assertStructure(String page)
    {
        // a. one overlap figure, inside the chart pane
        var overlap = "data-chart=\"overlap\"";
        assertEquals(1, occurrences(page, "<figure class=\"chart\" " + overlap), "one overlap figure");
        var paneStart = page.indexOf("<div class=\"chart-pane\"");
        var tablePane = page.indexOf("<div class=\"table-pane\">");
        assertTrue(paneStart >= 0 && tablePane > paneStart);
        var at = page.indexOf("<figure class=\"chart\" " + overlap);
        assertTrue(at > paneStart && at < tablePane, "the overlap figure lies in the chart pane");

        // b. per grouping one treemap, one bars figure and one view; only COMPANY is visible
        for (var grouping : GROUPINGS)
        {
            var hidden = grouping.equals("COMPANY") ? ">" : " hidden>";
            for (var kind : new String[] { "treemap", "bars" })
            {
                var figure = "<figure class=\"chart\" data-chart=\"" + kind + "\" data-grouping=\"" + grouping + "\"";
                assertEquals(1, occurrences(page, figure), kind + " figure of " + grouping);
                assertTrue(page.contains(figure + hidden), kind + " figure of " + grouping + " visibility");
            }
            var view = Pattern.compile("<section class=\"view\" id=\"view-" + grouping + "\"[^>]*>").matcher(page);
            assertTrue(view.find(), "view of " + grouping);
            assertEquals(grouping.equals("COMPANY") ? 0 : 1, view.group().endsWith(" hidden>") ? 1 : 0,
                            "view visibility of " + grouping);
            assertFalse(view.find(), "one view of " + grouping);
        }
        assertEquals(GROUPINGS.length, occurrences(page, "<section class=\"view\""));

        // c. treemap and bars of the Company grouping have the same box
        var treemapBox = Pattern.compile("<svg class=\"treemap\" data-grouping=\"COMPANY\" viewBox=\"([^\"]*)\"")
                        .matcher(page);
        var barsBox = Pattern.compile("<figure class=\"chart\" data-chart=\"bars\" data-grouping=\"COMPANY\"[\\s\\S]*?"
                        + "<svg class=\"bars\" viewBox=\"([^\"]*)\"").matcher(page);
        assertTrue(treemapBox.find() && barsBox.find(), "both Company charts are drawn");
        assertEquals(treemapBox.group(1), barsBox.group(1));
        assertEquals("0 0 640 380", treemapBox.group(1));

        // d. all breakdown tables have the same head cell classes
        var heads = Pattern.compile("<table class=\"breakdown\"[^>]*><thead>([\\s\\S]*?)</thead>").matcher(page);
        var first = (String) null;
        int tables = 0;
        while (heads.find())
        {
            tables++;
            var classes = new java.util.ArrayList<String>();
            var th = Pattern.compile("<th class=\"([^\"]*)\"").matcher(heads.group(1));
            while (th.find())
                classes.add(th.group(1));
            var sequence = String.join("|", classes);
            if (first == null)
                first = sequence;
            assertEquals(first, sequence);
        }
        assertEquals(GROUPINGS.length, tables);
        assertEquals("rank|name|num|num|num|num share", first);

        // e. every company block has a detail row with the contributions
        var blocks = Pattern.compile("<tbody class=\"block company\"[\\s\\S]*?</tbody>").matcher(page);
        int companies = 0;
        while (blocks.find())
        {
            companies++;
            assertTrue(blocks.group().contains("<tr class=\"detail\">"), "detail row");
            assertTrue(blocks.group().contains("<ul class=\"contrib\">"), "contributions list");
            assertTrue(blocks.group().indexOf("<tr class=\"detail\">") < blocks.group().indexOf("<ul class=\"contrib\">"));
        }
        assertTrue(companies > 0, "company blocks");

        // f. a grouped treemap has one zoom overlay per group, the Company treemap none
        var maps = Pattern.compile("<svg class=\"treemap\" data-grouping=\"([A-Z_]+)\"[\\s\\S]*?</svg>").matcher(page);
        int grouped = 0;
        while (maps.find())
        {
            var groups = occurrences(maps.group(), "class=\"tm-group\"");
            var zooms = occurrences(maps.group(), "class=\"tm-zoom\"");
            if (maps.group(1).equals("COMPANY"))
                assertEquals(0, zooms, "no overlays in the Company treemap");
            else if (groups > 0)
            {
                grouped++;
                assertEquals(groups, zooms, "overlays of " + maps.group(1));
            }
        }
        assertTrue(grouped > 0, "at least one grouped treemap");

        // g. the chart tabs
        for (var tab : new String[] { "treemap", "bars", "overlap" })
            assertTrue(page.contains("role=\"tab\" id=\"tab-" + tab + "\" data-chart=\"" + tab + "\""), tab);

        // h. explanations and the rows of the data status
        assertTrue(page.contains("<details class=\"hint\">"));
        var funds = page.indexOf("<table class=\"funds\"");
        assertTrue(funds >= 0, "fund table");
        var fundTable = page.substring(funds, page.indexOf("</table>", funds));
        assertTrue(fundTable.contains("title=\"") && fundTable.contains("<tr class=\"detail\">"));

        // i. stable layout
        assertTrue(page.contains("table-layout:fixed"));
        assertTrue(page.contains("scrollbar-gutter:stable"));
    }
}
