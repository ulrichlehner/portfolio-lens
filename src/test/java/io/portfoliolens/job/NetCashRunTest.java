package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import io.portfoliolens.page.PageWriter;
import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.testing.SyntheticUniverse;

/**
 * Funds whose identified holdings sum to more than 100%, balanced by negative cash and currency
 * rows without identifier: the page uses the real weights and shows the net cash on its own.
 */
public class NetCashRunTest
{
    private static final String TOKEN = "synthetic-pp-token";
    private static final Pattern NEGATIVE_SIZE = Pattern.compile("(width|height)=\"-");
    private static final Pattern COVERAGE_WIDTH = Pattern.compile("<rect class=\"cov-[a-z-]+\" x=\"\\d+\" y=\"0\" width=\"(\\d+)\"");

    @TempDir
    Path temp;

    private Instant now = Instant.parse("2026-07-20T08:00:00Z");

    private void run(SyntheticUniverse universe) throws Exception
    {
        universe.resetCounts();
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
            env.put(LensConfig.CACHE_DIR, temp.resolve("cache").toString());
            env.put(LensConfig.OUTPUT_DIR, temp.resolve("out").toString());
            var settings = LensConfig.load(env, Optional.empty(), temp, true).settings().orElseThrow();
            new LensRun(settings, new LensRun.Environment(universe, Clock.fixed(now, ZoneOffset.UTC), () -> {
            })).run();
        }
    }

    private String page() throws Exception
    {
        return Files.readString(temp.resolve("out").resolve(PageWriter.PAGE), StandardCharsets.UTF_8);
    }

    private static int compositionRequests(SyntheticUniverse universe)
    {
        return universe.requestsByPath().getOrDefault("api.eulerpool.com/api/1/etf/holdings/{isin}", 0);
    }

    @Test
    public void testFundWithNetCashIsUsedAndTheNetCashShownOnItsOwn() throws Exception
    {
        var universe = new SyntheticUniverse(60, 2, 0);
        universe.netCash(SyntheticUniverse.fundIsin(0), -3);
        run(universe);

        var page = page();
        assertTrue(page.contains("Net cash of funds"), "table row and summary item");
        var note = page.indexOf("Not shown: net cash of funds");
        assertTrue(note > 0, "note under treemap and coverage bar");
        assertTrue(page.indexOf("Not shown: net cash of funds", note + 1) > 0, "a note under both charts");
        assertTrue(page.substring(note, note + 60).matches("(?s)Not shown: net cash of funds EUR -[0-9.,]+ \\(-[0-9.,]+.*"),
                        page.substring(note, note + 60));
        assertTrue(page.contains("includes net cash -3"), "data status row of the fund");
        assertFalse(page.contains("Rejected"), "no fund is rejected");
        assertFalse(page.contains("USD CASH"), "cash rows are never shown by name");
        assertFalse(NEGATIVE_SIZE.matcher(page).find(), "no negative chart segment");
        int width = 0;
        var widths = COVERAGE_WIDTH.matcher(page);
        while (widths.find())
            width += Integer.parseInt(widths.group(1));
        assertTrue(width > 0 && width <= 1000, "coverage bar within its width: " + width);
    }

    @Test
    public void testPageWithoutNetCashShowsNoNetCashLine() throws Exception
    {
        var universe = new SyntheticUniverse(60, 2, 0);
        run(universe);
        var page = page();
        assertFalse(page.contains("Net cash of funds"));
        assertFalse(page.contains("Not shown: net cash"));
    }

    @Test
    public void testNetCashBeyondTheLimitIsRejectedWithItsReason() throws Exception
    {
        var universe = new SyntheticUniverse(60, 2, 0);
        universe.netCash(SyntheticUniverse.fundIsin(0), -12);
        run(universe);
        var page = page();
        assertTrue(page.contains("Rejected: negative net cash beyond 10"), "data status names the reason");
        assertFalse(page.contains("Not shown: net cash"));
    }

    /**
     * A fund rejected under the earlier rules is requested and evaluated again by the first run
     * after the upgrade, although its input is unchanged and its fund cadence has not passed.
     */
    @Test
    public void testFundRejectedUnderOlderRulesIsEvaluatedAgainAfterTheUpgrade() throws Exception
    {
        var universe = new SyntheticUniverse(60, 2, 0);
        // the first run rejects the fund, as the old rule did for any negative net
        universe.netCash(SyntheticUniverse.fundIsin(0), -12);
        run(universe);
        assertEquals(2, compositionRequests(universe));
        assertTrue(page().contains("Rejected: negative net cash"));

        // the state as an earlier version wrote it: no rules version, another fingerprint
        var stateFile = temp.resolve("cache").resolve(RunState.FILE);
        var state = JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
        state.remove("validationRules");
        state.addProperty("fingerprint", "written-by-an-earlier-version");
        Files.writeString(stateFile, new GsonBuilder().setPrettyPrinting().create().toJson(state));

        // the provider's answer would now be accepted
        universe.netCash(SyntheticUniverse.fundIsin(0), -3);
        now = now.plus(Duration.ofMinutes(10));
        run(universe);
        assertEquals(1, compositionRequests(universe), "only the rejected fund is requested again");
        assertTrue(page().contains("includes net cash -3"));
        assertFalse(page().contains("Rejected"));

        // the next run on unchanged input does not request it again
        now = now.plus(Duration.ofMinutes(10));
        run(universe);
        assertEquals(0, compositionRequests(universe));
    }

    @Test
    public void testWithoutTheRulesChangeARejectedFundWaitsForItsCadence() throws Exception
    {
        var universe = new SyntheticUniverse(60, 2, 0);
        universe.netCash(SyntheticUniverse.fundIsin(0), -12);
        run(universe);
        universe.netCash(SyntheticUniverse.fundIsin(0), -3);
        now = now.plus(Duration.ofMinutes(10));
        run(universe);
        assertEquals(0, compositionRequests(universe), Map.of().toString());
    }
}
