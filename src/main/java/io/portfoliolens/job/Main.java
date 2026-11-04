package io.portfoliolens.job;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.portfoliolens.Version;
import io.portfoliolens.http.HttpGet;
import io.portfoliolens.http.JdkHttpGet;
import io.portfoliolens.source.PpClient;
import io.portfoliolens.source.PpException;

/** Command-line entry point: {@code lens run} and {@code lens check}. */
public final class Main
{
    private static final String USAGE = """
                    Usage: lens <command> [--config <file>]

                      run     read Portfolio Performance, update the breakdown and write index.html
                      check   validate the configuration and PP access; no provider requests, no page

                    Configuration comes from LENS_* environment variables, optionally from a properties
                    file given with --config (the environment wins). See the README for all settings.
                    """;

    private Main()
    {
    }

    public static void main(String[] args)
    {
        configureLogging();
        System.exit(run(args, System.getenv(), Path.of(System.getProperty("user.home")), System.out,
                        new LensRun.Environment(new JdkHttpGet(Version.userAgent()), Clock.systemDefaultZone(),
                                        () -> sleep(1100))));
    }

    /** Runs a command and returns the exit status; separated from {@link #main} for tests. */
    public static int run(String[] args, Map<String, String> environment, Path home, PrintStream out,
                    LensRun.Environment dependencies)
    {
        var log = Logger.getLogger(Main.class.getName());
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h") || args[0].equals("help"))
        {
            out.print(USAGE);
            return args.length == 0 ? ExitStatus.CONFIG : ExitStatus.OK;
        }
        var command = args[0];
        Optional<Path> configFile = Optional.empty();
        for (int i = 1; i < args.length; i++)
        {
            if (args[i].equals("--config") && i + 1 < args.length)
                configFile = Optional.of(Path.of(args[++i]));
            else
            {
                log.severe("unknown argument; secrets are never accepted as arguments. " + USAGE);
                return ExitStatus.CONFIG;
            }
        }
        if (!command.equals("run") && !command.equals("check"))
        {
            log.severe("unknown command " + command + "\n" + USAGE);
            return ExitStatus.CONFIG;
        }

        var loaded = LensConfig.load(environment, configFile, home, command.equals("run"));
        if (loaded.settings().isEmpty())
        {
            loaded.errors().forEach(error -> log.severe("configuration: " + error));
            return ExitStatus.CONFIG;
        }
        var settings = loaded.settings().get();
        try
        {
            return command.equals("run") ? run(settings, dependencies) : check(settings, dependencies.http(), out);
        }
        catch (RuntimeException e)
        {
            log.log(Level.SEVERE, "unexpected error: " + e.getClass().getName());
            return ExitStatus.SOFTWARE;
        }
    }

    private static int run(LensConfig.Settings settings, LensRun.Environment dependencies)
    {
        var log = Logger.getLogger(Main.class.getName());
        log.info("Portfolio Lens " + Version.get() + " run started");
        try
        {
            var result = new LensRun(settings, dependencies).run();
            log.info("run finished: " + result.name().toLowerCase());
            return ExitStatus.OK;
        }
        catch (LensRun.Failure e)
        {
            log.severe(e.getMessage());
            return e.exitStatus();
        }
    }

    private static int check(LensConfig.Settings settings, HttpGet http, PrintStream out)
    {
        var log = Logger.getLogger(Main.class.getName());
        int status = ExitStatus.OK;
        try
        {
            var client = new PpClient(settings.ppUrl(), settings.ppToken().orElseThrow(), http);
            var version = client.version();
            out.println("Portfolio Performance REST API: " + version.apiVersion()
                            + version.applicationVersion().map(v -> " (PP " + v + ")").orElse(""));
            if (version.major() != 1)
            {
                log.severe("this Lens version needs REST API 1.x");
                status = ExitStatus.UNAVAILABLE;
            }
            else
            {
                var dummy = new LensRun(settings, new LensRun.Environment(http, Clock.systemUTC(), () -> {
                }));
                var file = dummy.selectFile(client.files());
                out.println("File: " + file.id() + file.alias().map(a -> " (alias " + a + ")").orElse(""));
            }
        }
        catch (PpException e)
        {
            log.severe(e.getMessage());
            status = ExitStatus.UNAVAILABLE;
        }
        out.println("Eulerpool API key: " + (settings.eulerpoolKey().isPresent() ? "set" : "not set"));
        out.println(LensConfig.LOCAL_STORAGE_CONFIRMED + ": " + settings.localStorageConfirmed());
        out.println("Register lookups (GLEIF): " + (settings.registerLookup() ? "on" : "off"));
        if (settings.unconfirmedKey())
        {
            log.severe(LensRun.unconfirmedWarning());
            if (status == ExitStatus.OK)
                status = ExitStatus.CONFIG;
        }
        if (status == ExitStatus.OK)
            out.println("check passed");
        return status;
    }

    private static void sleep(long millis) throws java.io.InterruptedIOException
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException();
        }
    }

    /** One line per event on standard error, without stack traces of provider payloads. */
    static void configureLogging()
    {
        var root = Logger.getLogger("");
        for (var handler : root.getHandlers())
            root.removeHandler(handler);
        var handler = new ConsoleHandler();
        handler.setFormatter(new Formatter()
        {
            private final DateTimeFormatter time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

            @Override
            public String format(LogRecord record)
            {
                return time.format(Instant.ofEpochMilli(record.getMillis())) + " " + record.getLevel().getName() + " "
                                + formatMessage(record) + System.lineSeparator();
            }
        });
        var level = Optional.ofNullable(System.getenv("LENS_LOG_LEVEL")).map(Level::parse).orElse(Level.INFO);
        handler.setLevel(level);
        root.setLevel(level);
        root.addHandler(handler);
    }
}
