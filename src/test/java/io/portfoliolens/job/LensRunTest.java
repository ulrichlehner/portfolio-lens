package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.testing.Fixtures;
import io.portfoliolens.testing.SyntheticProviders;

/** Whole runs against the fake PP server and synthetic provider answers (lens-job spec). */
public class LensRunTest
{
    private static final String TOKEN = "synthetic-pp-token";
    private static final String KEY = "synthetic-provider-key";

    @TempDir
    Path temp;

    private FakePpServer pp;
    private final SyntheticProviders http = new SyntheticProviders();
    private Instant now = Instant.parse("2026-07-20T08:00:00Z");
    private final List<String> log = new ArrayList<>();
    private final Handler handler = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            synchronized (log)
            {
                log.add(record.getLevel() + " " + record.getMessage());
            }
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

    @BeforeEach
    public void setUp() throws IOException
    {
        pp = new FakePpServer(TOKEN).serveStandard();
        Logger.getLogger("io.portfoliolens").addHandler(handler);
        Logger.getLogger("io.portfoliolens").setLevel(Level.INFO);
    }

    @AfterEach
    public void tearDown()
    {
        Logger.getLogger("io.portfoliolens").removeHandler(handler);
        pp.close();
    }

    private Map<String, String> env(boolean key, boolean confirmed)
    {
        var env = new HashMap<String, String>();
        env.put(LensConfig.PP_URL, pp.baseUrl().toString());
        env.put(LensConfig.PP_TOKEN, TOKEN);
        env.put(LensConfig.CACHE_DIR, temp.resolve("cache").toString());
        env.put(LensConfig.OUTPUT_DIR, temp.resolve("out").toString());
        if (key)
            env.put(LensConfig.EULERPOOL_API_KEY, KEY);
        if (confirmed)
            env.put(LensConfig.LOCAL_STORAGE_CONFIRMED, "true");
        return env;
    }

    private LensRun run(Map<String, String> env)
    {
        var settings = LensConfig.load(env, Optional.empty(), temp, true).settings().orElseThrow();
        return new LensRun(settings,
                        new LensRun.Environment(http, Clock.fixed(now, ZoneId.of("Europe/Berlin")), () -> {
                        }));
    }

    private String page() throws IOException
    {
        return Files.readString(temp.resolve("out/index.html"));
    }

    private StatusFile status()
    {
        return StatusFile.read(temp.resolve("out"));
    }

    private List<String> logLines()
    {
        synchronized (log)
        {
            return List.copyOf(log);
        }
    }

    @Test
    public void testFullRunWritesPageWithGetOnlyAndNoSecretsOrAmountsInLogs() throws Exception
    {
        assertEquals(LensRun.Result.WRITTEN, run(env(true, true)).run());

        var page = page();
        assertTrue(page.contains("<title>Breakdown by Company</title>"));
        assertTrue(page.contains("Apple"), "decomposed constituent shown: " + logLines() + " " + http.requests().stream().map(r -> r.uri().getPath() + "?" + r.uri().getQuery()).toList());
        assertTrue(page.contains("Microsoft"));
        assertFalse(page.contains(TOKEN));
        assertFalse(page.contains(KEY));
        assertFalse(page.contains(temp.toString()));
        assertEquals(Optional.of("written"), status().get("result"));
        assertEquals(Optional.of("enabled"), status().get("providerData"));

        for (var request : pp.requests())
        {
            assertEquals("GET", request.method());
            assertFalse(request.hasHeader("Origin"));
        }
        assertFalse(http.requests().isEmpty());
        for (var request : http.requests())
        {
            var text = request.uri().toString();
            assertFalse(text.contains("Synthetic"), text);
            assertFalse(text.contains("12000"), text);
        }
        for (var line : logLines())
        {
            assertFalse(line.contains(TOKEN) || line.contains(KEY), line);
            assertFalse(line.contains("Synthetic World") || line.contains("12000") || line.contains("1840"), line);
        }
        assertTrue(logLines().stream().anyMatch(l -> l.contains("wrote index.html")), logLines().toString());
        var state = Files.readString(temp.resolve("cache/state.json"));
        assertFalse(state.contains(KEY) || state.contains("12000") || state.contains("Synthetic"), state);
    }

    @Test
    public void testNothingChangedSkipsWithoutProviderRequests() throws Exception
    {
        run(env(true, true)).run();
        var before = page();
        var requests = http.requests().size();
        now = now.plus(Duration.ofMinutes(5));

        assertEquals(LensRun.Result.SKIPPED, run(env(true, true)).run());
        assertEquals(requests, http.requests().size());
        assertEquals(before, page());
        assertEquals(Optional.of(now.toString()), status().get("lastCheckedAt"));
        assertEquals(Optional.of("skipped"), status().get("result"));
    }

    @Test
    public void testPriceChangeRecalculatesWithoutProviderRequests() throws Exception
    {
        run(env(true, true)).run();
        var requests = http.requests().size();
        now = now.plus(Duration.ofMinutes(10));
        pp.answer("/v1/files/" + FakePpServer.FILE_ID + "/holdings", 200,
                        Fixtures.read("pp/holdings.json").replace("\"value\": 12000.00", "\"value\": 12600.00"));

        assertEquals(LensRun.Result.WRITTEN, run(env(true, true)).run());
        assertEquals(requests, http.requests().size(), "compositions within their cadence are not requested");
        assertTrue(page().contains("EUR 23,440.00"), "page recalculated with the new securities value");
    }

    @Test
    public void testUnconfirmedKeyMakesNoRequestsDeletesProviderDataAndWarns() throws Exception
    {
        run(env(true, true)).run();
        assertTrue(Files.list(temp.resolve("cache/compositions")).findAny().isPresent());
        var requests = http.requests().size();
        log.clear();

        assertEquals(LensRun.Result.WRITTEN, run(env(true, false)).run());
        assertEquals(requests, http.requests().size());
        assertTrue(Files.list(temp.resolve("cache/compositions")).findAny().isEmpty(), "provider data deleted");
        var page = page();
        assertTrue(page.contains("data-kind=\"UNCONFIRMED\""));
        assertTrue(page.contains("LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true"));
        assertTrue(page.indexOf("id=\"banner\"") < page.indexOf("class=\"summary\""), "banner above the summary");
        assertFalse(page.contains(">Apple<"), "no provider-derived exposure");
        assertEquals(Optional.of("unconfirmed"), status().get("providerData"));
        assertTrue(logLines().stream().anyMatch(l -> l.startsWith("WARNING") && l.contains("LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true")));

        // the warning repeats on a skipped run
        log.clear();
        now = now.plus(Duration.ofMinutes(5));
        assertEquals(LensRun.Result.SKIPPED, run(env(true, false)).run());
        assertTrue(logLines().stream().anyMatch(l -> l.startsWith("WARNING") && l.contains("is not true")));
        assertEquals(Optional.of("unconfirmed"), status().get("providerData"));
    }

    @Test
    public void testWithoutKeyNoRequestsAndNoKeyBanner() throws Exception
    {
        assertEquals(LensRun.Result.WRITTEN, run(env(false, false)).run());
        assertTrue(http.requests().isEmpty());
        assertTrue(page().contains("data-kind=\"NO_KEY\""));
        assertEquals(Optional.of("no-key"), status().get("providerData"));
    }

    @Test
    public void testOverlappingRunIsLocked() throws Exception
    {
        Files.createDirectories(temp.resolve("cache"));
        try (var channel = FileChannel.open(temp.resolve("cache/lens.lock"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE); var lock = channel.lock())
        {
            assertEquals(LensRun.Result.LOCKED, run(env(true, true)).run());
        }
        assertTrue(pp.requests().isEmpty(), "a locked run does not read PP");
    }

    @Test
    public void testLostStateStartsEmpty() throws Exception
    {
        Files.createDirectories(temp.resolve("cache"));
        Files.writeString(temp.resolve("cache/state.json"), "{ not json");
        assertEquals(LensRun.Result.WRITTEN, run(env(true, true)).run());
        assertTrue(logLines().stream().anyMatch(l -> l.contains("state file was unreadable")));
    }

    @Test
    public void testUnreachablePpKeepsPageAndRecordsFailure() throws Exception
    {
        run(env(true, true)).run();
        var before = page();
        pp.close();
        now = now.plus(Duration.ofHours(1));
        var failure = assertThrows(LensRun.Failure.class, () -> run(env(true, true)).run());
        assertEquals(ExitStatus.UNAVAILABLE, failure.exitStatus());
        assertEquals(before, page());
        assertEquals(Optional.of("failed"), status().get("result"));
        assertTrue(status().get("lastSuccessAt").isPresent());
        assertTrue(status().get("lastFailureAt").isPresent());
    }

    @Test
    public void testExhaustedBudgetLeavesLookupsPendingAcrossRuns() throws Exception
    {
        var env = env(true, true);
        env.put(LensConfig.REQUEST_BUDGET, "0");
        assertEquals(LensRun.Result.WRITTEN, run(env).run());
        assertTrue(http.requests().isEmpty());
        assertTrue(Integer.parseInt(status().get("pendingLookups").orElseThrow()) > 0);
        var json = com.google.gson.JsonParser.parseString(Files.readString(temp.resolve("out/status.json")))
                        .getAsJsonObject().getAsJsonObject("budget");
        assertTrue(json.get("exhausted").getAsBoolean());
        assertEquals(0, json.get("capacityUnits").getAsLong());
        assertTrue(page().contains("id=\"budget\""));
        assertTrue(page().contains("units used in the last 24 hours; remaining lookups wait"));
        // pending work is due again on the next run, which still cannot request anything
        now = now.plus(Duration.ofMinutes(5));
        assertEquals(LensRun.Result.WRITTEN, run(env).run());
        assertTrue(http.requests().isEmpty());
    }

    @Test
    public void testRunTimeLimitLeavesTheRestPending() throws Exception
    {
        var env = env(true, true);
        env.put(LensConfig.RUN_TIMEOUT, "PT0.3S");
        http.delay(400);
        assertEquals(LensRun.Result.WRITTEN, run(env).run());
        assertTrue(Integer.parseInt(status().get("pendingLookups").orElseThrow()) > 0);
        assertTrue(logLines().stream().anyMatch(l -> l.contains("run time limit reached")));
    }

    @Test
    public void testUnwritableOutputDirectory() throws Exception
    {
        var env = env(false, false);
        var file = Files.writeString(temp.resolve("a-file"), "x");
        env.put(LensConfig.OUTPUT_DIR, file.resolve("out").toString());
        var failure = assertThrows(LensRun.Failure.class, () -> run(env).run());
        assertEquals(ExitStatus.CANT_CREATE, failure.exitStatus());
        assertTrue(failure.getMessage().contains(LensConfig.OUTPUT_DIR), failure.getMessage());
    }

    private int main(Map<String, String> env, ByteArrayOutputStream out, String... args)
    {
        return Main.run(args, env, temp, new PrintStream(out, true, StandardCharsets.UTF_8),
                        new LensRun.Environment(http, Clock.fixed(now, ZoneOffset.UTC), () -> {
                        }));
    }

    @Test
    public void testCheckFailsForKeyWithoutConfirmation()
    {
        var out = new ByteArrayOutputStream();
        assertEquals(ExitStatus.CONFIG, main(env(true, false), out, "check"));
        assertTrue(logLines().stream().anyMatch(l -> l.contains("LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true")));
        assertTrue(http.requests().isEmpty(), "check makes no provider requests");
        assertFalse(Files.exists(temp.resolve("out/index.html")));
    }

    @Test
    public void testCheckPassesAndNamesFileAndVersion()
    {
        var out = new ByteArrayOutputStream();
        assertEquals(ExitStatus.OK, main(env(true, true), out, "check"));
        var text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("REST API: 1.2.0"), text);
        assertTrue(text.contains("(alias main)"), text);
        assertTrue(text.contains("check passed"), text);
        assertFalse(text.contains(KEY) || text.contains(TOKEN), text);
    }

    @Test
    public void testCheckWithMissingTokenAndSecretArgument()
    {
        var env = env(false, false);
        env.remove(LensConfig.PP_TOKEN);
        assertEquals(ExitStatus.CONFIG, main(env, new ByteArrayOutputStream(), "check"));
        assertTrue(logLines().stream().anyMatch(l -> l.contains(LensConfig.PP_TOKEN)));
        assertEquals(ExitStatus.CONFIG, main(env(false, false), new ByteArrayOutputStream(), "run", "--token", TOKEN));
    }

    @Test
    public void testMainRunExitStatus()
    {
        assertEquals(ExitStatus.OK, main(env(true, true), new ByteArrayOutputStream(), "run"));
        assertTrue(Files.exists(temp.resolve("out/index.html")));
        assertTrue(Files.exists(temp.resolve("out/status.json")));
    }
}
