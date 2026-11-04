package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.testing.SyntheticUniverse;

/**
 * Runs on an unchanged portfolio, including companies the provider and the register do not know:
 * later runs make almost no requests and never resolve less than earlier ones.
 */
public class UnchangedRunsTest
{
    private static final String TOKEN = "synthetic-pp-token";
    private static final Pattern COUNTS = Pattern.compile("resolved=(\\d+), assumed=(\\d+)");

    @TempDir
    Path temp;

    private final List<String> log = new ArrayList<>();
    private final Handler handler = new Handler()
    {
        @Override
        public synchronized void publish(LogRecord record)
        {
            log.add(record.getMessage());
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
        {
        }
    };
    private Instant now = Instant.parse("2026-07-20T08:00:00Z");

    @BeforeEach
    public void setUp()
    {
        Logger.getLogger("io.portfoliolens").addHandler(handler);
    }

    @AfterEach
    public void tearDown()
    {
        Logger.getLogger("io.portfoliolens").removeHandler(handler);
    }

    record Outcome(int resolved, int requests, Map<String, Integer> byPath, List<String> log)
    {
    }

    private Outcome run(SyntheticUniverse universe, Map<String, String> extra) throws Exception
    {
        universe.resetCounts();
        synchronized (handler)
        {
            log.clear();
        }
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
            env.putAll(extra);
            var settings = LensConfig.load(env, Optional.empty(), temp, true).settings().orElseThrow();
            new LensRun(settings, new LensRun.Environment(universe, Clock.fixed(now, ZoneOffset.UTC), () -> {
            })).run();
        }
        List<String> lines;
        synchronized (handler)
        {
            lines = List.copyOf(log);
        }
        int resolved = -1;
        for (var line : lines)
        {
            var m = COUNTS.matcher(line);
            if (m.find())
                resolved = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
        }
        var outcome = new Outcome(resolved, universe.providerRequests(), universe.requestsByPath(), lines);
        System.out.println("UNCHANGED RUN at " + now + ": resolved=" + resolved + " requests=" + outcome.requests()
                        + " " + outcome.byPath());
        return outcome;
    }

    @Test
    public void testLaterRunsOnUnchangedInputMakeAlmostNoRequests() throws Exception
    {
        var universe = new SyntheticUniverse(600, 4, 6);
        var first = run(universe, Map.of());
        var results = new ArrayList<Outcome>();
        for (int i = 0; i < 3; i++)
        {
            now = now.plus(Duration.ofMinutes(10));
            results.add(run(universe, Map.of()));
        }
        for (var later : results)
        {
            assertTrue(later.resolved() >= first.resolved(), first.resolved() + " then " + later.resolved());
            assertTrue(later.requests() <= 5, "requests " + later.requests() + " " + later.byPath());
        }
    }

    @Test
    public void testRunsAfterTransientErrorsSettleQuickly() throws Exception
    {
        var universe = new SyntheticUniverse(600, 4, 6);
        universe.failEvery(97);
        var first = run(universe, Map.of());
        universe.failEvery(0);
        var results = new ArrayList<Outcome>();
        for (int i = 0; i < 4; i++)
        {
            now = now.plus(Duration.ofMinutes(10));
            results.add(run(universe, Map.of()));
        }
        // the retries of the second run are allowed; after that only a handful of requests
        for (var later : results)
            assertTrue(later.resolved() >= first.resolved(), first.resolved() + " then " + later.resolved());
        for (var later : results.subList(1, results.size()))
            assertTrue(later.requests() <= 5, "requests " + later.requests() + " " + later.byPath());
    }

    @Test
    public void testOneAlwaysFailingCompanyDoesNotMakeEveryRunExpensive() throws Exception
    {
        var universe = new SyntheticUniverse(600, 4, 6);
        universe.alwaysFail(SyntheticUniverse.companyIsin(20));
        var first = run(universe, Map.of());
        var results = new ArrayList<Outcome>();
        for (int i = 0; i < 4; i++)
        {
            now = now.plus(Duration.ofMinutes(10));
            results.add(run(universe, Map.of()));
        }
        for (var later : results)
        {
            assertTrue(later.resolved() >= first.resolved(), first.resolved() + " then " + later.resolved());
            assertTrue(later.requests() <= 5, "requests " + later.requests() + " " + later.byPath());
        }
    }

    @Test
    public void testStoppedLookupsNameTheActualLimit() throws Exception
    {
        var universe = new SyntheticUniverse(300, 2, 3);
        // every second answer fails: failures in a row put the account on hold
        universe.failEvery(2);
        var first = run(universe, Map.of());
        var line = first.log().stream().filter(l -> l.startsWith("Breakdown classification")).findFirst().orElseThrow();
        assertTrue(!line.contains("BUDGET_EXHAUSTED"), "the budget is not used up: " + line);
        var status = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(temp.resolve("out/status.json")))
                        .getAsJsonObject();
        assertTrue(!status.getAsJsonObject("budget").get("exhausted").getAsBoolean(), status.toString());
        // the resolutions recover once the provider answers again
        universe.failEvery(0);
        int resolved = first.resolved();
        for (int i = 0; i < 6; i++)
        {
            now = now.plus(Duration.ofHours(2));
            var next = run(universe, Map.of());
            assertTrue(next.resolved() >= resolved, resolved + " then " + next.resolved());
            resolved = next.resolved();
        }
        var clean = new SyntheticUniverse(300, 2, 3);
        now = Instant.parse("2026-07-20T08:00:00Z");
        var reference = run(clean, Map.of(LensConfig.CACHE_DIR, temp.resolve("reference").toString()));
        assertTrue(resolved == reference.resolved(), "recovered " + resolved + " of " + reference.resolved());
    }
}
