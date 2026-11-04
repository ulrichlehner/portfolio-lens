package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.portfoliolens.page.LookThroughViewPresenter.ChartCompany;
import io.portfoliolens.page.LookThroughViewPresenter.RowKey;
import io.portfoliolens.page.TreemapLayout.Rect;
import io.portfoliolens.testing.Fixtures;

/**
 * The page script draws zoomed treemap levels with its own copy of the layout. On a shared
 * fixture it must give exactly what the Java layout, merge, label and colour give.
 */
public class TreemapParityTest
{
    @TempDir
    Path temp;

    private record Item(int index, double weight, boolean last)
    {
    }

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

    private JsonObject runScript(String fixture) throws Exception
    {
        var script = Script.LAYOUT + """
                        const input=JSON.parse(require('fs').readFileSync(process.argv[2],'utf8'));
                        const items=input.items.map((it,i)=>({w:it.w,last:it.last,i:i}));
                        const tiles=Lens.squarify(items,input.area).map(t=>[t.item.i,t.x,t.y,t.w,t.h]);
                        let sum=0;input.members.forEach(m=>sum+=m.w);
                        const merged=Lens.merge(input.members.map((m,i)=>({w:m.w,d:m.d,i:i})),input.area.w*input.area.h/sum,input.minArea);
                        const labels=input.labels.map(l=>Lens.label(l[0],l[1],l[2],l[3]));
                        const colors=input.colors.map(Lens.tileColor);
                        console.log(JSON.stringify({tiles:tiles,shown:merged.shown.map(m=>m.i),others:merged.others.map(m=>m.i),
                          weight:merged.weight,threshold:merged.threshold,labels:labels,colors:colors}));
                        """;
        var file = Files.writeString(temp.resolve("parity.js"), script);
        var input = Files.writeString(temp.resolve("input.json"), fixture);
        var process = new ProcessBuilder("node", file.toString(), input.toString()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return JsonParser.parseString(output.trim()).getAsJsonObject();
    }

    @Test
    public void testJavaAndPageScriptGiveTheSameTreemap() throws Exception
    {
        assumeTrue(nodeAvailable(), "Node.js runs in the dev image");
        var fixture = Fixtures.read("treemap/parity.json");
        var input = JsonParser.parseString(fixture).getAsJsonObject();
        var js = runScript(fixture);

        // layout
        var area = input.getAsJsonObject("area");
        var items = new ArrayList<Item>();
        var array = input.getAsJsonArray("items");
        for (int i = 0; i < array.size(); i++)
        {
            var it = array.get(i).getAsJsonObject();
            items.add(new Item(i, it.get("w").getAsDouble(), it.get("last").getAsBoolean()));
        }
        var tiles = TreemapLayout.squarify(items, Item::weight, Item::last, new Rect(area.get("x").getAsDouble(),
                        area.get("y").getAsDouble(), area.get("w").getAsDouble(), area.get("h").getAsDouble()));
        var jsTiles = js.getAsJsonArray("tiles");
        assertEquals(tiles.size(), jsTiles.size());
        for (int i = 0; i < tiles.size(); i++)
        {
            var t = tiles.get(i);
            var j = jsTiles.get(i).getAsJsonArray();
            assertEquals(t.item().index(), j.get(0).getAsInt(), "order at " + i);
            assertEquals(t.rect().x(), j.get(1).getAsDouble(), 0, "x at " + i);
            assertEquals(t.rect().y(), j.get(2).getAsDouble(), 0, "y at " + i);
            assertEquals(t.rect().width(), j.get(3).getAsDouble(), 0, "width at " + i);
            assertEquals(t.rect().height(), j.get(4).getAsDouble(), 0, "height at " + i);
        }

        // merge of small tiles
        var members = input.getAsJsonArray("members");
        var companies = new ArrayList<ChartCompany>();
        double sum = 0;
        for (int i = 0; i < members.size(); i++)
        {
            var m = members.get(i).getAsJsonObject();
            var w = m.get("w").getAsDouble();
            var d = m.get("d").getAsDouble();
            sum += w;
            companies.add(new ChartCompany(new RowKey(List.of("member", i)), "Member " + i, BigDecimal.valueOf(w * d),
                            BigDecimal.valueOf(w - w * d), BigDecimal.valueOf(w), Optional.empty(), Optional.empty()));
        }
        var merged = PageWriter.merge(companies, 640 * 380 / sum);
        assertEquals(merged.shown().size(), js.getAsJsonArray("shown").size());
        assertEquals(merged.others().size(), js.getAsJsonArray("others").size());
        assertEquals(merged.otherWeight(), js.get("weight").getAsDouble(), 1e-6);
        assertEquals(merged.threshold(), js.get("threshold").getAsDouble(), 1e-9);

        // merged-tile text and tile colour
        var labels = input.getAsJsonArray("labels");
        var jsLabels = js.getAsJsonArray("labels");
        for (int i = 0; i < labels.size(); i++)
        {
            var l = labels.get(i).getAsJsonArray();
            var denominator = l.get(3).getAsLong();
            assertEquals(PageWriter.mergedLabel(l.get(0).getAsInt(), l.get(1).getAsDouble(), l.get(2).getAsDouble(),
                            denominator > 0 ? Optional.of(denominator) : Optional.empty()), jsLabels.get(i).getAsString());
        }
        JsonArray colors = input.getAsJsonArray("colors");
        for (int i = 0; i < colors.size(); i++)
            assertEquals(ChartColors.tileColor(colors.get(i).getAsDouble()), js.getAsJsonArray("colors").get(i).getAsString());
    }

    @Test
    public void testMergedLabelText()
    {
        // 3,882 companies worth 331,000.00 of 1,000,000.00 (hundredths), each below 512.50
        assertEquals("3,882 companies, 33.1%, each below 0.052%",
                        PageWriter.mergedLabel(3882, 33_100_000, 51_250, Optional.of(100_000_000L)));
        assertEquals("1 company, 0.0%, each below 0.003%", PageWriter.mergedLabel(1, 1000, 3000, Optional.of(100_000_000L)));
        assertEquals("12 companies", PageWriter.mergedLabel(12, 500, 40, Optional.empty()));
        assertEquals("0.05", PageWriter.upperPercent(0.05));
        assertEquals("0.052", PageWriter.upperPercent(0.05125));
        assertEquals("1.3", PageWriter.upperPercent(1.21));
    }
}
