package io.portfoliolens.job;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.portfoliolens.Version;
import io.portfoliolens.core.CompanyEnrichmentCache;
import io.portfoliolens.core.CompanyEnrichmentService;
import io.portfoliolens.core.CompositionInstrument;
import io.portfoliolens.core.CompositionPersistencePolicy;
import io.portfoliolens.core.CompositionPersistencePolicy.StorageMode;
import io.portfoliolens.core.CompositionRefreshCoordinator.Disposition;
import io.portfoliolens.core.CompositionRefreshCoordinator.Refresh;
import io.portfoliolens.core.CompositionStore;
import io.portfoliolens.core.FundOverlap;
import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.http.HttpGet;
import io.portfoliolens.page.LookThroughBanner;
import io.portfoliolens.page.LookThroughDataStatus;
import io.portfoliolens.page.PageInput;
import io.portfoliolens.page.PageInput.ProviderState;
import io.portfoliolens.page.PageWriter;
import io.portfoliolens.provider.CompositionCredentials;
import io.portfoliolens.provider.EulerpoolFundCompositionProvider;
import io.portfoliolens.provider.GleifCompanyRegister;
import io.portfoliolens.source.PpClient;
import io.portfoliolens.source.PpException;
import io.portfoliolens.source.PpModel.Holdings;
import io.portfoliolens.source.PpModel.Instrument;
import io.portfoliolens.source.PpModel.PpFile;
import io.portfoliolens.source.RestValuationAdapter;

/**
 * One run of the job (design D8): lock, read PP, decide whether anything changed, resolve
 * compositions and company data within budget and time, calculate, and write the page and the
 * status file. Nothing lives between runs except the files in the cache and output directories.
 */
public final class LensRun
{
    private static final Logger LOG = Logger.getLogger(LensRun.class.getName());

    /** The local provider account; an opaque name, never a login. */
    public static final ProviderAccount ACCOUNT = new ProviderAccount(EulerpoolFundCompositionProvider.ID, "local");

    /** Upper bound of the time between two full runs while inputs stay unchanged. */
    static final Duration MAX_SKIP = Duration.ofHours(24);

    public enum Result
    {
        WRITTEN, SKIPPED, LOCKED
    }

    /** Outside dependencies, replaceable in tests. */
    public record Environment(HttpGet http, Clock clock, GleifCompanyRegister.Pause registerPause)
    {
    }

    /** A failure that ends the run, with the exit status it maps to. */
    public static final class Failure extends Exception
    {
        private static final long serialVersionUID = 1L;
        private final int exitStatus;

        public Failure(int exitStatus, String message)
        {
            super(message);
            this.exitStatus = exitStatus;
        }

        public int exitStatus()
        {
            return exitStatus;
        }
    }

    private final LensConfig.Settings settings;
    private final Environment environment;

    public LensRun(LensConfig.Settings settings, Environment environment)
    {
        this.settings = Objects.requireNonNull(settings);
        this.environment = Objects.requireNonNull(environment);
    }

    public Result run() throws Failure
    {
        var cacheDir = settings.cacheDir();
        createPrivateDirectory(cacheDir, LensConfig.CACHE_DIR);
        try (var channel = FileChannel.open(cacheDir.resolve("lens.lock"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE))
        {
            FileLock lock;
            try
            {
                lock = channel.tryLock();
            }
            catch (OverlappingFileLockException e)
            {
                lock = null;
            }
            if (lock == null)
            {
                LOG.info("another run is active on this cache directory; nothing to do");
                return Result.LOCKED;
            }
            try (lock)
            {
                return runLocked();
            }
        }
        catch (IOException e)
        {
            throw new Failure(ExitStatus.CANT_CREATE,
                            "cannot use the cache directory (" + LensConfig.CACHE_DIR + "): " + e.getClass().getSimpleName());
        }
    }

    private Result runLocked() throws Failure
    {
        var clock = environment.clock();
        var started = clock.instant();
        var status = StatusFile.read(settings.outputDir().orElseThrow());
        if (settings.unconfirmedKey())
            LOG.warning(unconfirmedWarning());

        RunState state;
        try
        {
            state = RunState.read(settings.cacheDir(), ACCOUNT).orElseGet(RunState::empty);
        }
        catch (IOException e)
        {
            LOG.warning("the run state file was unreadable and is replaced by an empty state");
            state = RunState.empty();
        }

        // read Portfolio Performance
        PpModel model;
        try
        {
            model = readPortfolio();
        }
        catch (PpException e)
        {
            status.failed(started, e.getMessage(), providerState());
            writeStatus(status);
            throw new Failure(ExitStatus.UNAVAILABLE, e.getMessage());
        }
        var scope = new RestValuationAdapter().value(model.holdings(), model.instruments());

        // skip when nothing changed and no provider work is due
        var fingerprint = fingerprint(model);
        if (state.fingerprint().equals(Optional.of(fingerprint)) && state.nextDueAt().filter(started::isBefore).isPresent()
                        && Files.exists(settings.outputDir().orElseThrow().resolve(PageWriter.PAGE)))
        {
            LOG.info("inputs unchanged and no provider work due; page left as it is");
            status.checked(started, providerState());
            writeStatus(status);
            return Result.SKIPPED;
        }

        var executor = Executors.newFixedThreadPool(2, daemon("lens-provider"));
        var enrichmentExecutor = Executors.newFixedThreadPool(2, daemon("lens-enrichment"));
        try
        {
            var deadline = started.plus(settings.runTimeout());
            var providerHttp = new CountingHttpGet(environment.http());
            var provider = new EulerpoolFundCompositionProvider(this::key,
                            EulerpoolFundCompositionProvider.transport(providerHttp), clock,
                            account -> settings.localStorageConfirmed());
            var finalState = state;
            var credentials = new CompositionCredentials(account -> key(account),
                            account -> finalState.keyRevision(settings.eulerpoolKey()));
            var store = LookThroughSession.storeIn(createPrivateDirectory(settings.cacheDir().resolve("compositions"),
                            LensConfig.CACHE_DIR), clock);
            var session = new LookThroughSession(provider, provider, credentials, clock, executor, store,
                            settings.requestBudget());
            var companies = new CompanyEnrichmentCache(createPrivateDirectory(settings.cacheDir().resolve("companies"),
                            LensConfig.CACHE_DIR), clock);
            var policy = provider.getPersistencePolicy(ACCOUNT);
            var preference = Optional.<Boolean>empty();
            // a withdrawn confirmation deletes the provider data before any other work
            session.configure(policy, preference);
            companies.configure(policy, preference);
            companies.preload();

            session.budget().restore(state.budget());
            state.attempts().forEach(attempt -> session.coordinator().restore(ACCOUNT, attempt));

            var funds = funds(scope);
            var refreshes = new LinkedHashMap<CompositionInstrument, Refresh>();
            int pending = 0;
            if (settings.providerEnabled())
            {
                var futures = new LinkedHashMap<CompositionInstrument, CompletableFuture<Refresh>>();
                for (var fund : funds)
                    futures.put(fund, session.refresh(ACCOUNT, fund, false));
                await(futures.values(), deadline);
                for (var entry : futures.entrySet())
                {
                    var future = entry.getValue();
                    if (future.isDone() && !future.isCompletedExceptionally())
                        refreshes.put(entry.getKey(), future.join());
                    else
                        pending++;
                }
            }

            var retained = funds.stream().map(fund -> session.read(ACCOUNT, fund)).toList();
            var result = new LookThroughAnalysis().calculate(scope.valuation(), retained, session.classifications());
            LOG.info(LookThroughAnalysis.diagnostics(result));

            var attributes = CompanyEnrichmentService.Snapshot.empty();
            if (settings.providerEnabled())
            {
                var enrichment = session.enableEnrichment(ACCOUNT, provider::profile, provider::revenueRegions,
                                new GleifCompanyRegister(GleifCompanyRegister.transport(providerHttp),
                                                environment.registerPause(), clock),
                                settings::registerLookup, companies, enrichmentExecutor,
                                (task, delay) -> {
                                    // retries are left to the next run
                                });
                session.enrich(result.companies());
                waitIdle(enrichment, deadline);
                attributes = enrichment.snapshot();
                enrichment.close();
                waitIdle(enrichment, clock.instant().plusSeconds(5));
                companies.flush();
            }

            var effective = policy.effectiveModeDetail(preference);
            var global = new LookThroughDataStatus.GlobalFacts(
                            effective.mode() == StorageMode.PERSISTENT ? CompositionStore.State.PERSISTENT
                                            : CompositionStore.State.SESSION_ONLY,
                            effective);
            var facts = funds.stream().map(fund -> {
                var refresh = refreshes.get(fund);
                return refresh != null ? LookThroughDataStatus.fundFacts(refresh, ACCOUNT, fund, false)
                                : LookThroughDataStatus.fundFacts(session.read(ACCOUNT, fund), ACCOUNT, fund,
                                                settings.providerEnabled());
            }).toList();
            var dataStatus = LookThroughDataStatus.present(clock, global, facts,
                            Optional.of(settings.providerEnabled()));

            var suspended = session.budget().isSuspended(ACCOUNT);
            var banner = LookThroughBanner.select(new LookThroughBanner.Facts(
                            Optional.of(settings.eulerpoolKey().isPresent()), suspended,
                            !funds.isEmpty(), scope.valuation().positions().isEmpty()
                                            && scope.valuation().diagnostics().isEmpty(),
                            settings.unconfirmedKey()));

            var nextAttempt = refreshes.values().stream().filter(r -> r.disposition() != Disposition.COMPLETED)
                            .flatMap(r -> r.retryAt().stream()).min(Comparator.naturalOrder());
            var pendingLookups = pending + (int) refreshes.values().stream()
                            .filter(r -> r.disposition() == Disposition.DEFERRED || r.disposition() == Disposition.BUSY)
                            .count() + attributes.pending();

            var input = new PageInput(clock.instant(), clock.getZone(), model.file().label(), result, attributes,
                            dataStatus, banner, providerState(), FundOverlap.of(result), scope.accountCash(),
                            scope.unsupportedHoldingTypes(), pendingLookups, nextAttempt, Version.get());
            try
            {
                PageWriter.write(settings.outputDir().orElseThrow(), input);
            }
            catch (IOException e)
            {
                status.failed(started, "cannot write the page to " + LensConfig.OUTPUT_DIR, providerState());
                writeStatus(status);
                throw new Failure(ExitStatus.CANT_CREATE, "cannot write the page to the output directory ("
                                + LensConfig.OUTPUT_DIR + "): " + e.getClass().getSimpleName());
            }

            // what the next run needs
            state.setBudget(session.budget().snapshot());
            state.setAttempts(session.coordinator().attempts(ACCOUNT));
            state.setFingerprint(Optional.of(fingerprint));
            state.setNextDueAt(Optional.of(nextDue(started, refreshes, pendingLookups)));
            try
            {
                state.write(settings.cacheDir(), ACCOUNT);
            }
            catch (IOException e)
            {
                LOG.warning("the run state could not be saved; the next run starts with an empty state");
            }
            status.written(started, clock.instant(), providerState(), pendingLookups, nextAttempt);
            writeStatus(status);
            LOG.info("wrote " + PageWriter.PAGE + ": " + funds.size() + " instruments looked at, "
                            + providerHttp.count() + " provider and register requests, " + pendingLookups + " lookups pending");
            if (settings.unconfirmedKey())
                LOG.warning(unconfirmedWarning());
            return Result.WRITTEN;
        }
        finally
        {
            executor.shutdownNow();
            enrichmentExecutor.shutdownNow();
        }
    }

    /** The warning for a key without the local storage confirmation (design D6). */
    public static String unconfirmedWarning()
    {
        return "an Eulerpool API key is configured, but " + LensConfig.LOCAL_STORAGE_CONFIRMED
                        + " is not true: no provider data is requested or used. Set " + LensConfig.CONFIRMATION_HINT
                        + " if your Eulerpool plan allows storing its data locally.";
    }

    private ProviderState providerState()
    {
        if (settings.eulerpoolKey().isEmpty())
            return ProviderState.NO_KEY;
        return settings.localStorageConfirmed() ? ProviderState.ENABLED : ProviderState.UNCONFIRMED;
    }

    private Optional<String> key(ProviderAccount account)
    {
        return ACCOUNT.equals(account) && settings.providerEnabled() ? settings.eulerpoolKey() : Optional.empty();
    }

    /** The PP data of one run. */
    record PpModel(PpFile file, Holdings holdings, List<Instrument> instruments)
    {
    }

    private PpModel readPortfolio() throws PpException
    {
        var client = new PpClient(settings.ppUrl(), settings.ppToken().orElseThrow(), environment.http());
        var version = client.version();
        if (version.major() != 1)
            throw new PpException(PpException.Kind.INCOMPATIBLE, "Portfolio Performance offers REST API version "
                            + version.apiVersion() + "; this Lens version needs 1.x");
        var file = selectFile(client.files());
        var holdings = client.holdings(file.id(), settings.reportingCurrency());
        var instruments = client.instruments(file.id());
        LOG.info("read " + holdings.items().size() + " holdings and " + instruments.size()
                        + " instruments (PP REST API " + version.apiVersion() + ")");
        return new PpModel(file, holdings, instruments);
    }

    PpFile selectFile(List<PpFile> files) throws PpException
    {
        if (settings.ppFile().isPresent())
        {
            var wanted = settings.ppFile().get();
            var matches = files.stream()
                            .filter(f -> f.id().equals(wanted) || f.alias().filter(wanted::equals).isPresent()).toList();
            if (matches.size() == 1)
                return matches.get(0);
            if (matches.size() > 1)
                throw new PpException(PpException.Kind.AMBIGUOUS_ALIAS,
                                "The configured alias matches several files. Configure the file id instead.");
            throw new PpException(PpException.Kind.NOT_FOUND, "The file configured in " + LensConfig.PP_FILE
                            + " is not open and enabled in PP. Enabled files: " + describe(files));
        }
        if (files.size() == 1)
            return files.get(0);
        throw new PpException(PpException.Kind.FILE_SELECTION, (files.isEmpty()
                        ? "No file is open and enabled for the REST API in PP."
                        : "Several files are enabled in PP; set " + LensConfig.PP_FILE + " to one of: "
                                        + describe(files)));
    }

    private static String describe(List<PpFile> files)
    {
        return files.isEmpty() ? "none"
                        : String.join(", ", files.stream()
                                        .map(f -> f.id() + f.alias().map(a -> " (alias " + a + ")").orElse("")).toList());
    }

    /** Held instruments with a value and an ISIN, once per ISIN, as source rows for the provider. */
    private static List<CompositionInstrument> funds(RestValuationAdapter.Scope scope)
    {
        var byIsin = new LinkedHashMap<String, CompositionInstrument>();
        for (var position : scope.valuation().positions())
        {
            var instrument = position.security().asCompositionInstrument();
            var isin = instrument.identifiers().get(RestValuationAdapter.ISIN);
            if (isin != null)
                byIsin.putIfAbsent(isin, new CompositionInstrument(instrument.label(), Map.of(RestValuationAdapter.ISIN, isin)));
        }
        return List.copyOf(byIsin.values());
    }

    /**
     * SHA-256 over everything that changes the page except the clock: the holdings report, the
     * instrument identifiers, the non-secret settings and the Lens version.
     */
    String fingerprint(PpModel model)
    {
        var text = new StringBuilder();
        var holdings = model.holdings();
        text.append(holdings.date()).append('|').append(holdings.reportingCurrency()).append('|')
                        .append(model.file().id()).append('|').append(model.file().label()).append('\n');
        holdings.items().stream().sorted(Comparator.comparing(h -> h.type() + h.uuid())).forEach(h -> text
                        .append(h.type()).append('|').append(h.uuid()).append('|').append(h.name()).append('|')
                        .append(h.shares().map(s -> s.stripTrailingZeros().toPlainString()).orElse("")).append('|')
                        .append(h.valuation().value().stripTrailingZeros().toPlainString()).append('|')
                        .append(h.valuation().currency()).append('|')
                        .append(h.price().map(p -> p.value().stripTrailingZeros().toPlainString() + p.currency()
                                        + p.date().map(Object::toString).orElse("")).orElse(""))
                        .append('|').append(h.localValuation()
                                        .map(l -> l.value().stripTrailingZeros().toPlainString() + l.currency()).orElse(""))
                        .append('\n'));
        model.instruments().stream().sorted(Comparator.comparing(Instrument::uuid)).forEach(i -> text.append(i.uuid())
                        .append('|').append(i.name()).append('|').append(i.isin().orElse("")).append('|')
                        .append(i.currencyCode().orElse("")).append('\n'));
        text.append(settings.fingerprintPart()).append('|').append(Version.get());
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * When the next run has provider work even with unchanged inputs: at once when lookups are
     * pending, else at the earliest cadence or retry time, at the latest after one day.
     */
    private Instant nextDue(Instant now, Map<CompositionInstrument, Refresh> refreshes, int pending)
    {
        if (pending > 0)
            return now;
        var next = now.plus(MAX_SKIP);
        for (var refresh : refreshes.values())
            for (var at : refresh.retryAt().stream().toList())
                if (at.isBefore(next))
                    next = at.isAfter(now) ? at : now;
        return next;
    }

    private static void await(java.util.Collection<CompletableFuture<Refresh>> futures, Instant deadline)
    {
        var all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try
        {
            all.get(Math.max(0, Duration.between(Instant.now(), deadline).toMillis()), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            LOG.info("run time limit reached; remaining lookups stay pending for the next run");
        }
        catch (ExecutionException e)
        {
            LOG.log(Level.FINE, "a lookup failed", e.getCause().getClass().getSimpleName());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitIdle(CompanyEnrichmentService service, Instant deadline)
    {
        try
        {
            while (!service.isIdle() && Instant.now().isBefore(deadline))
                Thread.sleep(50);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private void writeStatus(StatusFile status) throws Failure
    {
        try
        {
            status.write(settings.outputDir().orElseThrow());
        }
        catch (IOException e)
        {
            throw new Failure(ExitStatus.CANT_CREATE, "cannot write to the output directory (" + LensConfig.OUTPUT_DIR
                            + "): " + e.getClass().getSimpleName());
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name)
    {
        return task -> {
            var thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Creates a directory readable only by the owner where the file system supports it. */
    static Path createPrivateDirectory(Path directory, String setting) throws Failure
    {
        try
        {
            Files.createDirectories(directory);
            if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null)
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            return directory.toRealPath();
        }
        catch (IOException | RuntimeException e)
        {
            throw new Failure(ExitStatus.CANT_CREATE,
                            "cannot create the directory named by " + setting + ": " + e.getClass().getSimpleName());
        }
    }

}
