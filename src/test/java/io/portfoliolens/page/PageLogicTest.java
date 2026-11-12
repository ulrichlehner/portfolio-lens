package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The page script keeps its address, the levels of the treemap, the breadcrumb and the search in
 * DOM-free functions. They run here in Node.js on a synthetic data block of about 300 companies,
 * and the results are compared with values derived from the same data.
 */
public class PageLogicTest
{
    private static final int COMPANIES = 300;
    private static final int ZEBRA = 270;
    private static final int EMPTY = 299;
    private static final double AREA = 640.0 * 380.0;
    private static final double MIN_AREA = 140;

    @TempDir
    Path temp;

    private JsonObject data;
    private final long[] direct = new long[COMPANIES];
    private final long[] indirect = new long[COMPANIES];

    private static boolean nodeAvailable()
    {
        try
        {
            return new ProcessBuilder("node", "--version").start().waitFor(10, TimeUnit.SECONDS);
        }
        catch (IOException | InterruptedException e)
        {
            return false;
        }
    }

    private long total(int i)
    {
        return direct[i] + indirect[i];
    }

    @BeforeEach
    public void buildData()
    {
        var companies = new JsonArray();
        for (int i = 0; i < COMPANIES; i++)
        {
            long total = i == EMPTY ? 0 : Math.round(1e12 * Math.pow(0.93, i));
            direct[i] = Math.round(total * 0.4);
            indirect[i] = total - direct[i];
            var name = i == ZEBRA ? "Synthetic Zebra Inc." : String.format("Synthetic Company %03d", i);
            var row = new JsonArray();
            row.add(i);
            row.add(name);
            row.add(direct[i]);
            row.add(indirect[i]);
            row.add(i);
            row.add(new JsonArray());
            companies.add(row);
        }

        var groups = new JsonObject();
        var sector = new JsonArray();
        var first = new JsonArray();
        // reverse order and a company without weight, so that sorting and filtering matter
        first.add(member(EMPTY, 1));
        for (int i = 9; i >= 0; i--)
            first.add(member(i, 1));
        sector.add(first);
        var second = new JsonArray();
        second.add(member(20, 1));
        second.add(member(21, 1));
        sector.add(second);
        groups.add("SECTOR", sector);
        var region = new JsonArray();
        var north = new JsonArray();
        north.add(member(3, 0.25));
        north.add(member(5, 1));
        region.add(north);
        groups.add("REVENUE_REGION", region);

        data = new JsonObject();
        data.add("c", companies);
        data.add("g", groups);
        data.addProperty("chartWidth", 640);
        data.addProperty("chartHeight", 380);
        data.addProperty("minTileArea", 140);
    }

    private static JsonArray member(int company, double factor)
    {
        var m = new JsonArray();
        m.add(company);
        m.add(factor);
        m.add(company);
        return m;
    }

    /** Calls {@code Lens.<function>} with the arguments; the text "$D" stands for the data block. */
    private JsonElement call(String function, Object... args) throws Exception
    {
        assumeTrue(nodeAvailable(), "Node.js runs in the dev image");
        var gson = new Gson();
        var request = new JsonObject();
        request.add("D", data);
        request.addProperty("fn", function);
        request.add("args", gson.toJsonTree(args));
        var script = Script.LAYOUT + """
                        const input=JSON.parse(require('fs').readFileSync(process.argv[2],'utf8'));
                        const args=input.args.map(a=>a==='$D'?input.D:a);
                        const r=Lens[input.fn].apply(null,args);
                        console.log(JSON.stringify(r===undefined?null:r));
                        """;
        var file = Files.writeString(temp.resolve("logic.js"), script);
        var input = Files.writeString(temp.resolve("input.json"), gson.toJson(request));
        var process = new ProcessBuilder("node", file.toString(), input.toString()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return JsonParser.parseString(output.trim());
    }

    private static List<Integer> ints(JsonArray array, String field)
    {
        var list = new ArrayList<Integer>();
        for (var e : array)
            list.add(field == null ? e.getAsInt() : e.getAsJsonObject().get(field).getAsInt());
        return list;
    }

    private static void assertAddress(JsonObject a, String grouping, String zoomKey, Integer depth, String chart)
    {
        if (grouping == null)
            assertTrue(a.get("g").isJsonNull());
        else
            assertEquals(grouping, a.get("g").getAsString());
        if (zoomKey == null)
            assertTrue(a.get("zoom").isJsonNull());
        else
        {
            assertEquals(zoomKey, a.getAsJsonObject("zoom").get("key").getAsString());
            assertEquals(depth.intValue(), a.getAsJsonObject("zoom").get("depth").getAsInt());
        }
        assertEquals(chart, a.get("chart").getAsString());
    }

    private JsonObject address(String hash) throws Exception
    {
        return call("parseAddress", hash).getAsJsonObject();
    }

    @Test
    public void testParseAddress() throws Exception
    {
        assertAddress(address("#grouping=SECTOR&zoom=3.1"), "SECTOR", "3", 1, "treemap");
        assertAddress(address("#grouping=COUNTRY&zoom=2.0"), "COUNTRY", "2", 0, "treemap");
        assertAddress(address("#chart=overlap&grouping=COMPANY"), "COMPANY", null, null, "treemap");
        assertAddress(address("#grouping=SECTOR&chart=overlap"), "SECTOR", null, null, "treemap");
        assertAddress(address("#grouping=COMPANY&zoom=all.2&foo=bar"), "COMPANY", "all", 2, "treemap");
        assertAddress(address(""), null, null, null, "treemap");
        assertAddress(address("#nonsense"), null, null, null, "treemap");
        assertAddress(address("#grouping=SECTOR&zoom=x.y"), "SECTOR", null, null, "treemap");
        assertAddress(address("#grouping=SECTOR&chart=pie"), "SECTOR", null, null, "treemap");
    }

    @Test
    public void testFormatAddress() throws Exception
    {
        var state = new JsonObject();
        state.addProperty("g", "SECTOR");
        var zoom = new JsonObject();
        zoom.addProperty("key", "3");
        zoom.addProperty("depth", 2);
        state.add("zoom", zoom);
        state.addProperty("chart", "bars");
        var hash = call("formatAddress", state).getAsString();
        assertAddress(address(hash), "SECTOR", "3", 2, "bars");

        state.addProperty("chart", "treemap");
        var treemap = call("formatAddress", state).getAsString();
        assertFalse(treemap.contains("chart="), treemap);
        assertAddress(address(treemap), "SECTOR", "3", 2, "treemap");

        var plain = new JsonObject();
        plain.addProperty("g", "SECTOR");
        plain.addProperty("chart", "treemap");
        assertEquals("#grouping=SECTOR", call("formatAddress", plain).getAsString());
    }

    @Test
    public void testLevelOfAGroup() throws Exception
    {
        var level = call("levelMembers", "$D", "SECTOR", "0", 0).getAsJsonArray();
        // companies 0 to 9 by weight, the company without weight is left out
        var expected = new ArrayList<Integer>();
        for (int i = 0; i < 10; i++)
            expected.add(i);
        assertEquals(expected, ints(level, "idx"));
        for (int i = 0; i < level.size(); i++)
        {
            var m = level.get(i).getAsJsonObject();
            assertEquals(total(i), m.get("w").getAsDouble(), 0);
            assertEquals((double) direct[i] / total(i), m.get("d").getAsDouble(), 1e-12);
            if (i > 0)
                assertTrue(level.get(i - 1).getAsJsonObject().get("w").getAsDouble() > m.get("w").getAsDouble());
        }
    }

    @Test
    public void testPartOfACompany() throws Exception
    {
        var level = call("levelMembers", "$D", "REVENUE_REGION", "0", 0).getAsJsonArray();
        assertEquals(List.of(3, 5), ints(level, "idx").stream().sorted().toList());
        for (var e : level)
        {
            var m = e.getAsJsonObject();
            var i = m.get("idx").getAsInt();
            var factor = i == 3 ? 0.25 : 1.0;
            assertEquals(total(i) * factor, m.get("w").getAsDouble(), 1e-6);
            assertEquals((double) direct[i] / total(i), m.get("d").getAsDouble(), 1e-12);
        }
        assertEquals(5, level.get(0).getAsJsonObject().get("idx").getAsInt());
    }

    @Test
    public void testLevelThatDoesNotExist() throws Exception
    {
        assertTrue(call("levelMembers", "$D", "SECTOR", "99", 0).isJsonNull());
        assertTrue(call("levelMembers", "$D", "SECTOR", "all", 1).isJsonNull());
        assertTrue(call("levelMembers", "$D", "COMPANY", "3", 0).isJsonNull());
        assertTrue(call("levelMembers", "$D", "COMPANY", "all", 0).isJsonNull());
    }

    @Test
    public void testMergedLevels() throws Exception
    {
        double sum = 0;
        var members = new JsonArray();
        for (int i = 0; i < COMPANIES; i++)
        {
            if (total(i) <= 0)
                continue;
            sum += total(i);
            var m = new JsonObject();
            m.addProperty("w", total(i));
            m.addProperty("d", (double) direct[i] / total(i));
            m.addProperty("i", i);
            members.add(m);
        }
        var merged = call("merge", members, AREA / sum, MIN_AREA).getAsJsonObject();
        var others = ints(merged.getAsJsonArray("others"), "i");
        assertFalse(others.isEmpty());
        assertTrue(others.size() < COMPANIES - 1);

        var one = call("levelMembers", "$D", "COMPANY", "all", 1).getAsJsonArray();
        assertEquals(others, ints(one, "idx"));

        var two = ints(call("levelMembers", "$D", "COMPANY", "all", 2).getAsJsonArray(), "idx");
        assertFalse(two.isEmpty());
        assertTrue(two.size() < others.size());
        assertTrue(others.containsAll(two));

        assertTrue(call("levelMembers", "$D", "COMPANY", "all", 40).isJsonNull());
    }

    private static JsonArray equalMembers(int count, int firstIndex)
    {
        var members = new JsonArray();
        for (int i = 0; i < count; i++)
        {
            var m = new JsonObject();
            m.addProperty("w", 1);
            m.addProperty("d", 0.5);
            m.addProperty("i", firstIndex + i);
            members.add(m);
        }
        return members;
    }

    @Test
    public void testExploredLevelShowsMembersUntilHalfIsMerged() throws Exception
    {
        var members = equalMembers(2800, 0);
        var first = call("merge", members, AREA / 2800, MIN_AREA, true).getAsJsonObject();
        assertEquals(1400, first.getAsJsonArray("shown").size());
        assertEquals(1400, first.getAsJsonArray("others").size());

        // the merged rest is the next level: everything fits and nothing is merged
        var next = call("merge", first.getAsJsonArray("others"), AREA / 1400, MIN_AREA, true).getAsJsonObject();
        assertEquals(1400, next.getAsJsonArray("shown").size());
        assertEquals(0, next.getAsJsonArray("others").size());
    }

    @Test
    public void testExploredLevelShowsAtMostThreeThousandMembers() throws Exception
    {
        var level = call("merge", equalMembers(7000, 0), AREA / 7000, MIN_AREA, true).getAsJsonObject();
        assertEquals(3000, level.getAsJsonArray("shown").size());
        assertEquals(4000, level.getAsJsonArray("others").size());
    }

    @Test
    public void testExploredLevelWithSmallRestIsUnchanged() throws Exception
    {
        var members = new JsonArray();
        for (int i = 0; i < 40; i++)
        {
            var m = new JsonObject();
            m.addProperty("w", i < 10 ? 100 : 1);
            m.addProperty("d", 0.5);
            m.addProperty("i", i);
            members.add(m);
        }
        double scale = AREA / 1030;
        var plain = call("merge", members, scale, 300);
        assertEquals(30, plain.getAsJsonObject().getAsJsonArray("others").size());
        assertEquals(plain, call("merge", members, scale, 300, true));
    }

    @Test
    public void testCrumbs() throws Exception
    {
        var all = call("crumbs", "all", 2, null).getAsJsonArray();
        assertEquals(List.of("All", "Smaller companies", "Smaller companies (2)"), names(all));
        assertTrue(all.get(0).getAsJsonObject().get("level").isJsonNull());
        assertLevel(all.get(1), "all", 1);
        assertLevel(all.get(2), "all", 2);

        var group = call("crumbs", "3", 1, "Synthetic Sector").getAsJsonArray();
        assertEquals(List.of("All", "Synthetic Sector", "Smaller companies"), names(group));
        assertTrue(group.get(0).getAsJsonObject().get("level").isJsonNull());
        assertLevel(group.get(1), "3", 0);
        assertLevel(group.get(2), "3", 1);
    }

    private static List<String> names(JsonArray crumbs)
    {
        var list = new ArrayList<String>();
        for (var e : crumbs)
            list.add(e.getAsJsonObject().get("name").getAsString());
        return list;
    }

    private static void assertLevel(JsonElement crumb, String key, int depth)
    {
        var level = crumb.getAsJsonObject().getAsJsonObject("level");
        assertNotNull(level);
        assertEquals(key, level.get("key").getAsString());
        assertEquals(depth, level.get("depth").getAsInt());
    }

    @Test
    public void testSearch() throws Exception
    {
        assertEquals(List.of(ZEBRA), ints(call("search", "$D", "zebra").getAsJsonArray(), null));
        assertEquals(List.of(ZEBRA), ints(call("search", "$D", "ZEBRA").getAsJsonArray(), null));

        // "company 01" matches the names 010 to 019
        var several = ints(call("search", "$D", "Company 01").getAsJsonArray(), null);
        var expected = new ArrayList<Integer>();
        for (int i = 10; i < 20; i++)
            expected.add(i);
        assertEquals(expected, several);

        assertTrue(call("search", "$D", "no such company").getAsJsonArray().isEmpty());
        assertTrue(call("search", "$D", "").getAsJsonArray().isEmpty());
        assertTrue(call("search", "$D", "   ").getAsJsonArray().isEmpty());
    }
}
