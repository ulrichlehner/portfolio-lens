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

/** A later run never resolves less than an earlier one because of budget, time or failures. */
public class RunsKeepResolutionsTest
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

    private int run(SyntheticUniverse universe, Map<String, String> extra) throws Exception
    {
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
            synchronized (handler)
            {
                log.clear();
            }
            new LensRun(settings, new LensRun.Environment(universe, Clock.fixed(now, ZoneOffset.UTC), () -> {
            })).run();
        }
        synchronized (handler)
        {
            for (var line : log)
            {
                var m = COUNTS.matcher(line);
                if (m.find())
                    return Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
            }
        }
        throw new AssertionError("no classification line in " + log);
    }

    @Test
    public void testExhaustedBudgetKeepsEarlierResolutions() throws Exception
    {
        var universe = new SyntheticUniverse(600, 4, 6);
        var first = run(universe, Map.of(LensConfig.REQUEST_BUDGET, "700"));
        now = now.plus(Duration.ofMinutes(10));
        var second = run(universe, Map.of(LensConfig.REQUEST_BUDGET, "700"));
        assertTrue(second >= first, "first " + first + ", second " + second + " " + log);
    }

    @Test
    public void testTransientFailuresKeepEarlierResolutions() throws Exception
    {
        var universe = new SyntheticUniverse(600, 4, 6);
        var first = run(universe, Map.of());
        try (var files = java.nio.file.Files.list(temp.resolve("cache/compositions")))
        {
            var names = files.map(f -> f.getFileName().toString().replaceAll("[0-9a-f]{16,}", "#")).toList();
            System.out.println("CACHE FILES " + names.size() + " " + names.stream()
                            .collect(java.util.stream.Collectors.groupingBy(n -> n, java.util.stream.Collectors.counting())));
        }
        System.out.println("REQUESTS " + universe.requestsByPath());
        universe.failEvery(7);
        now = now.plus(Duration.ofHours(25));
        var second = run(universe, Map.of());
        assertTrue(second >= first, "first " + first + ", second " + second + " " + log);
    }

    @Test
    public void testRunTimeLimitKeepsEarlierResolutionsAndContinues() throws Exception
    {
        var universe = new SyntheticUniverse(600, 4, 6);
        var full = run(new SyntheticUniverse(600, 4, 6), Map.of(LensConfig.CACHE_DIR, temp.resolve("other").toString()));
        var first = run(universe, Map.of(LensConfig.RUN_TIMEOUT, "PT0.5S"));
        int previous = first;
        for (int i = 0; i < 8; i++)
        {
            now = now.plus(Duration.ofMinutes(10));
            var next = run(universe, Map.of(LensConfig.RUN_TIMEOUT, "PT0.5S"));
            assertTrue(next >= previous, "run " + (i + 2) + ": " + previous + " then " + next);
            previous = next;
        }
        assertTrue(previous == full, "later runs reach the full result " + full + ", got " + previous);
    }
}
