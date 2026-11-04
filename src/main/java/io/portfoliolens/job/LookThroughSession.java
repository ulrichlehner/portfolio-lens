package io.portfoliolens.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.portfoliolens.provider.CompositionCredentials;
import io.portfoliolens.provider.CompositionCredentials.Mode;
import io.portfoliolens.provider.FundCompositionProvider;
import io.portfoliolens.provider.EulerpoolFundCompositionProvider;
import io.portfoliolens.core.CompanyEnrichmentCache;
import io.portfoliolens.core.CompanyEnrichmentService;
import io.portfoliolens.core.CompositionInstrument;
import io.portfoliolens.core.CompositionPersistencePolicy;
import io.portfoliolens.core.CompositionRefreshCoordinator;
import io.portfoliolens.core.CompositionRefreshCoordinator.Disposition;
import io.portfoliolens.core.CompositionRefreshCoordinator.Refresh;
import io.portfoliolens.core.CompositionRefreshCoordinator.Snapshot;
import io.portfoliolens.core.CompositionRequestBudget;
import io.portfoliolens.core.CompositionResult;
import io.portfoliolens.core.CompositionValidationResult;
import io.portfoliolens.core.InstrumentClassifier;
import io.portfoliolens.core.FundComposition;
import io.portfoliolens.core.InstrumentEvidence;
import io.portfoliolens.core.InstrumentResolution;
import io.portfoliolens.core.InstrumentResolver;
import io.portfoliolens.core.LookThroughResult;
import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.core.SessionCompositionStore;
import io.portfoliolens.core.SessionCompositionStore.NoComposition;
import io.portfoliolens.core.VersionedCompositionStore;
import io.portfoliolens.page.LookThroughRefreshController.RefreshGateway;

/**
 * One provider session shared across clients. Serial admission drains the held
 * portfolio without overflowing the coordinator's bounded transport capacity. Queued and
 * running requests coalesce by account and identifiers, independent of labels.
 * Credential storage and all request admission run off the UI thread.
 */
public final class LookThroughSession implements RefreshGateway
{
    private static final Logger LOG = Logger.getLogger(LookThroughSession.class.getName());

    private record Key(ProviderAccount account, Map<String, String> identifiers)
    {
    }

    private record Configuration(CompositionPersistencePolicy policy, Optional<Boolean> preference)
    {
    }

    /** Resolutions built from stored data for one stored composition. */
    private record Restored(FundComposition composition, List<InstrumentResolution> resolutions)
    {
    }

    private final CompositionCredentials credentials;
    private final Clock clock;
    private final Executor executor;
    private final SessionCompositionStore store;
    private final CompositionRefreshCoordinator coordinator;
    private final InstrumentClassifier classifier;
    private final CompositionRequestBudget budget;
    private final long classificationCost;
    private final Map<Key, CompletableFuture<List<InstrumentEvidence>>> evidenceCache = new HashMap<>();
    /** Identifiers known to have no stored answer, so reads do not repeat disk lookups. */
    private final Set<Key> storeMisses = new HashSet<>();
    /** Stored answers without composition per fund, read from the store once per configuration. */
    private final Map<Key, Optional<NoComposition>> noCompositions = new HashMap<>();
    /**
     * Resolutions of stored compositions, so repeated reads (status updates
     * on the user interface thread) do not resolve every constituent again.
     */
    private final Map<Key, Restored> restored = new HashMap<>();
    private final Map<Key, CompletableFuture<Refresh>> pending = new HashMap<>();
    private final Map<ProviderAccount, Configuration> configurations = new HashMap<>();
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private volatile CompanyEnrichmentService enrichment;
    /** Company profiles shared by classification and enrichment; null before enrichment is enabled. */
    private volatile CompanyEnrichmentCache companies;
    private final List<Runnable> enrichmentListeners = new CopyOnWriteArrayList<>();

    public LookThroughSession(FundCompositionProvider provider, InstrumentClassifier classifier,
                    CompositionCredentials credentials, Clock clock, Executor executor)
    {
        this(provider, classifier, credentials, clock, executor, new SessionCompositionStore(clock));
    }

    /** The store keeps compositions and classifications on disk only while its effective mode is persistent. */
    public LookThroughSession(FundCompositionProvider provider, InstrumentClassifier classifier,
                    CompositionCredentials credentials, Clock clock, Executor executor,
                    SessionCompositionStore store)
    {
        this(provider, classifier, credentials, clock, executor, store, DEFAULT_CAPACITY);
    }

    /** Request units per rolling 24 hours when nothing else is configured. */
    public static final long DEFAULT_CAPACITY = 20000;

    /**
     * @param capacity
     *            provider request units per rolling 24 hours, shared by compositions,
     *            classifications and enrichment
     */
    public LookThroughSession(FundCompositionProvider provider, InstrumentClassifier classifier,
                    CompositionCredentials credentials, Clock clock, Executor executor,
                    SessionCompositionStore store, long capacity)
    {
        this.credentials = credentials;
        this.clock = clock;
        this.executor = executor;
        this.classifier = classifier;
        this.store = store;
        // Conservative application budget, not a statement of plan entitlement.
        // Reserve the adapter's worst-case request count, including fallbacks;
        // the part a classification did not use is released afterwards.
        this.classificationCost = classifier instanceof EulerpoolFundCompositionProvider
                        ? EulerpoolFundCompositionProvider.CLASSIFICATION_REQUEST_COST : 1;
        this.budget = new CompositionRequestBudget(provider.getId(), clock, capacity, Duration.ofHours(24),
                        Duration.ofMinutes(15));
        this.coordinator = new CompositionRefreshCoordinator(provider, budget, clock, executor,
                        classificationExecutor(), 1, classificationCost, 5000, 1,
                        new CompositionRefreshCoordinator.EnrichmentLookup()
                        {
                            @Override
                            public List<InstrumentEvidence> lookup(ProviderAccount account,
                                            CompositionInstrument instrument) throws IOException
                            {
                                var requests = new AtomicInteger();
                                try
                                {
                                    return classifyCached(account, instrument, true, requests);
                                }
                                finally
                                {
                                    release(account, requests);
                                }
                            }

                            @Override
                            public Optional<List<InstrumentEvidence>> known(ProviderAccount account,
                                            CompositionInstrument instrument)
                            {
                                return knownEvidence(account, instrument);
                            }
                        });
    }

    /** The shared request budget, so a job can persist and restore it between runs. */
    public CompositionRequestBudget budget()
    {
        return budget;
    }

    /** The refresh coordinator, so a job can persist and restore attempts between runs. */
    public CompositionRefreshCoordinator coordinator()
    {
        return coordinator;
    }

    /**
     * Versioned store in the given private cache directory; session-only memory
     * when the directory is missing or cannot be prepared. The store writes
     * nothing until the account's effective mode is persistent.
     */
    public static SessionCompositionStore storeIn(Path directory, Clock clock)
    {
        if (directory == null)
            return new SessionCompositionStore(clock);
        try
        {
            Files.createDirectories(directory);
            if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null)
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            // The store refuses directories reached through links.
            return new VersionedCompositionStore(directory.toRealPath(), clock);
        }
        catch (IOException | RuntimeException e)
        {
            LOG.log(Level.WARNING, "composition cache unavailable, keeping data in memory: " + e.getClass().getSimpleName());
            return new SessionCompositionStore(clock);
        }
    }

    private static Executor classificationExecutor()
    {
        var sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            var thread = new Thread(task, "lookthrough-classification-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(8, factory);
    }

    /** Returns the part of a classification reservation that the requests made did not use. */
    private void release(ProviderAccount account, AtomicInteger requests)
    {
        budget.release(account, Math.max(0, classificationCost - requests.get()));
    }

    /** A classification answer of this session or the store, without any request. */
    private Optional<List<InstrumentEvidence>> knownEvidence(ProviderAccount account,
                    CompositionInstrument instrument)
    {
        if (!restoreEvidence(account, instrument))
            return Optional.empty();
        synchronized (evidenceCache)
        {
            var future = evidenceCache.get(classificationKey(account, instrument));
            return future != null && future.isDone() && !future.isCompletedExceptionally()
                            ? Optional.of(future.join())
                            : Optional.empty();
        }
    }

    /**
     * Constituents are classified from their company profile first, which
     * enrichment needs anyway; held instruments by search first, where a
     * fund is more likely.
     */
    private Optional<InstrumentEvidence> classify(ProviderAccount account, CompositionInstrument instrument,
                    boolean profileFirst, AtomicInteger requests) throws IOException
    {
        if (classifier instanceof EulerpoolFundCompositionProvider eulerpool)
            return eulerpool.classify(account, instrument, companies, profileFirst, requests);
        // Other classifiers do not report their requests; the reservation stays.
        requests.addAndGet((int) classificationCost);
        return classifier.classify(account, instrument);
    }

    /**
     * Session-wide classification cache: one provider answer per identifier, so
     * overlapping funds share their constituents without repeated lookups.
     * Requests counts the provider requests this call made.
     */
    private List<InstrumentEvidence> classifyCached(ProviderAccount account, CompositionInstrument instrument,
                    boolean profileFirst, AtomicInteger requests) throws IOException
    {
        var key = classificationKey(account, instrument);
        var created = new CompletableFuture<List<InstrumentEvidence>>();
        CompletableFuture<List<InstrumentEvidence>> future;
        synchronized (evidenceCache)
        {
            future = evidenceCache.get(key);
            if (future == null)
            {
                future = created;
                evidenceCache.put(key, future);
            }
        }
        if (future == created)
        {
            try
            {
                // Captured before the lookup, so an answer arriving after a
                // withdrawal of local storage is not written.
                var generation = store.generation(account);
                var stored = store.readEvidence(account, key.identifiers());
                if (stored.isPresent())
                {
                    future.complete(stored.get());
                }
                else
                {
                    var evidence = classify(account, instrument, profileFirst, requests).map(List::of)
                                    .orElseGet(List::of);
                    store.writeEvidence(account, key.identifiers(), evidence, generation);
                    future.complete(evidence);
                }
            }
            catch (IOException e)
            {
                synchronized (evidenceCache)
                {
                    evidenceCache.remove(key, future);
                }
                future.completeExceptionally(e);
            }
            catch (RuntimeException e)
            {
                synchronized (evidenceCache)
                {
                    evidenceCache.remove(key, future);
                }
                future.completeExceptionally(e);
            }
        }
        try
        {
            return future.get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        catch (ExecutionException e)
        {
            if (e.getCause() instanceof IOException io)
                throw io;
            if (e.getCause() instanceof RuntimeException runtime)
                throw runtime;
            throw new IOException(e.getCause());
        }
    }

    private Key classificationKey(ProviderAccount account, CompositionInstrument instrument)
    {
        var isin = instrument.identifiers().get(InstrumentEvidence.ISIN);
        return new Key(account, isin == null || isin.isBlank() ? instrument.identifiers()
                        : Map.of(InstrumentEvidence.ISIN, isin));
    }

    /** Loads a stored classification answer into the session cache; returns whether one is known. */
    private boolean restoreEvidence(ProviderAccount account, CompositionInstrument instrument)
    {
        var key = classificationKey(account, instrument);
        synchronized (evidenceCache)
        {
            if (evidenceCache.containsKey(key))
                return true;
            if (storeMisses.contains(key))
                return false;
        }
        var stored = store.readEvidence(account, key.identifiers());
        synchronized (evidenceCache)
        {
            if (stored.isEmpty())
            {
                storeMisses.add(key);
                return false;
            }
            evidenceCache.putIfAbsent(key, CompletableFuture.completedFuture(stored.get()));
            return true;
        }
    }

    /** Resolutions of a stored composition from stored classifications, without any provider request. */
    private List<InstrumentResolution> storedResolutions(ProviderAccount account, FundComposition composition)
    {
        var resolver = new InstrumentResolver();
        var resolutions = new ArrayList<InstrumentResolution>();
        for (var row : composition.constituents())
        {
            var instrument = row.instrument();
            restoreEvidence(account, instrument);
            List<InstrumentEvidence> known = null;
            synchronized (evidenceCache)
            {
                var future = evidenceCache.get(classificationKey(account, instrument));
                if (future != null && future.isDone() && !future.isCompletedExceptionally())
                    known = future.join();
            }
            // A stored answer counts as the answered lookup, as in the refresh.
            var answer = known;
            // without a stored answer the constituent waits for the catch-up of a later run
            resolutions.add(answer != null ? resolver.resolve(instrument, List.of(), ignored -> answer, () -> true)
                            : notRetrieved(resolver.resolve(instrument, List.of(), ignored -> List.of(), () -> false)));
        }
        return List.copyOf(resolutions);
    }

    private static InstrumentResolution notRetrieved(InstrumentResolution resolution)
    {
        return resolution.lookupStatus() == InstrumentResolution.LookupStatus.BUDGET_EXHAUSTED
                        ? resolution.withLookupStatus(InstrumentResolution.LookupStatus.NOT_RETRIEVED)
                        : resolution;
    }

    /** Retry state of one classification lookup that failed, kept between runs. */
    public record LookupBackoff(int failures, Instant nextAttempt)
    {
    }

    /**
     * What a catch-up did: lookups made, lookups still due (stopped by the time limit or the
     * budget), lookups on hold after failures, and the earliest retry of those on hold.
     */
    public record CatchUp(int looked, int due, int held, Optional<Instant> nextAttempt)
    {
    }

    /** First retry after one hour, then four times longer each failure, at most a week. */
    static Instant nextAttempt(Instant now, int failures)
    {
        var hours = Math.min(7 * 24, (long) Math.pow(4, Math.max(0, failures - 1)));
        return now.plus(Duration.ofHours(hours));
    }

    /**
     * Looks up the constituents of retained compositions that have no stored classification yet,
     * for example because an earlier lookup failed or a limit stopped it, without requesting the
     * composition again. Lookups that failed before wait for their backoff; new failures extend it.
     * Stops at the deadline or when the budget refuses.
     */
    public CatchUp catchUp(ProviderAccount account, List<CompositionInstrument> funds, Instant deadline,
                    Map<Map<String, String>, LookupBackoff> backoff)
    {
        int looked = 0;
        int due = 0;
        int onHold = 0;
        Optional<Instant> next = Optional.empty();
        var seen = new HashSet<Key>();
        for (var fund : funds)
        {
            var retained = store.read(account, fund).usableComposition();
            if (retained.isEmpty())
                continue;
            for (var row : retained.get().source().constituents())
            {
                var instrument = row.instrument();
                if (instrument.identifiers().isEmpty())
                    continue;
                var key = classificationKey(account, instrument);
                if (!seen.add(key) || restoreEvidence(account, instrument))
                    continue;
                var held = backoff.get(key.identifiers());
                if (held != null && held.nextAttempt().isAfter(clock.instant()))
                {
                    onHold++;
                    next = next.filter(at -> at.isBefore(held.nextAttempt())).or(() -> Optional.of(held.nextAttempt()));
                    continue;
                }
                if (Instant.now().isAfter(deadline) || !budget.acquirePermit(account, classificationCost))
                {
                    due++;
                    continue;
                }
                var requests = new AtomicInteger();
                try
                {
                    classifyCached(account, instrument, true, requests);
                    backoff.remove(key.identifiers());
                    looked++;
                }
                catch (IOException | RuntimeException e)
                {
                    var failures = held == null ? 1 : held.failures() + 1;
                    var retry = nextAttempt(clock.instant(), failures);
                    backoff.put(key.identifiers(), new LookupBackoff(failures, retry));
                    if (e instanceof CompositionRefreshCoordinator.EnrichmentFailure failure)
                        budget.record(account, failure.result());
                    onHold++;
                    next = next.filter(at -> at.isBefore(retry)).or(() -> Optional.of(retry));
                }
                finally
                {
                    release(account, requests);
                }
            }
        }
        synchronized (evidenceCache)
        {
            restored.clear();
        }
        return new CatchUp(looked, due, onHold, next);
    }

    /**
     * Stores the classification answers that a result used and the store does not hold yet, so a
     * later run can restore every resolution of this one.
     */
    public void persistResolutions(ProviderAccount account, List<InstrumentResolution> resolutions)
    {
        var generation = store.generation(account);
        for (var resolution : resolutions)
        {
            var status = resolution.lookupStatus();
            if (resolution.state() == InstrumentResolution.State.UNRESOLVED
                            || resolution.source().identifiers().isEmpty()
                            || status != InstrumentResolution.LookupStatus.ATTEMPTED
                                            && status != InstrumentResolution.LookupStatus.NOT_NEEDED)
                continue;
            var key = classificationKey(account, resolution.source());
            if (store.readEvidence(account, key.identifiers()).isPresent())
                continue;
            store.writeEvidence(account, key.identifiers(), resolution.evidence(), generation);
        }
    }

    public synchronized void configure(CompositionPersistencePolicy policy, Optional<Boolean> preference)
    {
        var configuration = new Configuration(policy, preference);
        if (!configuration.equals(configurations.put(policy.account(), configuration)))
        {
            store.configure(policy, preference);
            synchronized (evidenceCache)
            {
                storeMisses.clear();
                noCompositions.clear();
                restored.clear();
            }
        }
    }

    /** The stored provider answer that the fund has no composition, if known. */
    private Optional<NoComposition> storedNoComposition(ProviderAccount account, CompositionInstrument fund)
    {
        var key = new Key(account, fund.identifiers());
        synchronized (evidenceCache)
        {
            var known = noCompositions.get(key);
            if (known != null)
                return known;
        }
        var stored = store.readNoComposition(account, fund.identifiers());
        synchronized (evidenceCache)
        {
            noCompositions.put(key, stored);
        }
        return stored;
    }

    @Override
    public Snapshot read(ProviderAccount account, CompositionInstrument fund)
    {
        var latest = coordinator.read(account, fund);
        var retained = store.read(account, fund);
        var resolutions = latest.resolutions();
        // In a new process a stored composition is shown from stored classifications
        // until a successful refresh replaces them. This also holds when the latest
        // attempt of this run failed: a failed refresh must not lose the
        // classifications that the retained composition already had.
        if (resolutions.isEmpty() && retained.usableComposition().isPresent())
        {
            var composition = retained.usableComposition().get().source();
            var key = new Key(account, fund.identifiers());
            Restored known;
            synchronized (evidenceCache)
            {
                known = restored.get(key);
            }
            if (known != null && known.composition() == composition)
            {
                resolutions = known.resolutions();
            }
            else
            {
                restoreEvidence(account, fund);
                resolutions = storedResolutions(account, composition);
                synchronized (evidenceCache)
                {
                    restored.put(key, new Restored(composition, resolutions));
                }
            }
        }
        // A stored answer without composition shows as such after a restart.
        if (latest.latestAttempt().isEmpty() && latest.attemptedAt().isEmpty()
                        && retained.usableComposition().isEmpty())
        {
            var absent = storedNoComposition(account, fund);
            if (absent.isPresent())
                return new Snapshot(Optional.of(absent.get().storedAt()),
                                Optional.of(new CompositionResult(absent.get().status(), Optional.empty(),
                                                Optional.empty())),
                                Optional.empty(), latest.unverifiedComposition(), resolutions);
        }
        // Keep source previews and attempt dates even when validation rejected
        // the payload. Only the store decides whether data is usable/retained.
        // Stored provider weights without verified semantics stay unverified.
        var verified = retained.usableComposition().filter(CompositionValidationResult::semanticsVerified)
                        .map(CompositionValidationResult::source);
        var unverified = latest.unverifiedComposition().or(() -> retained.usableComposition()
                        .filter(value -> !value.semanticsVerified()).map(CompositionValidationResult::source));
        return new Snapshot(latest.attemptedAt(), latest.latestAttempt(), verified, unverified, resolutions);
    }

    @Override
    public synchronized CompletableFuture<Refresh> refresh(ProviderAccount account, CompositionInstrument fund,
                    boolean manual)
    {
        var key = new Key(account, fund.identifiers());
        var existing = pending.get(key);
        if (existing != null)
            return existing;

        var result = new CompletableFuture<Refresh>();
        pending.put(key, result);
        var work = tail.thenComposeAsync(ignored -> retrieve(account, fund, manual), executor);
        // Even failures must allow the next distinct fund to be admitted.
        tail = work.handle((value, failure) -> null);
        work.whenComplete((value, failure) -> {
            synchronized (this)
            {
                pending.remove(key, result);
            }
            if (failure != null)
                result.completeExceptionally(failure);
            else
                result.complete(value);
        });
        return result;
    }

    private CompletableFuture<Refresh> retrieve(ProviderAccount account, CompositionInstrument fund, boolean manual)
    {
        var state = credentials.state(account);
        coordinator.configurationChanged(account, state.revision());
        if (!manual && state.mode() != Mode.CONFIGURED)
            return CompletableFuture.completedFuture(new Refresh(Disposition.SUSPENDED, read(account, fund),
                            Optional.empty()));

        // A composition stored within the last cadence interval is current
        // enough: an automatic pass after a restart does not request it again.
        if (!manual)
        {
            var untouched = coordinator.read(account, fund).attemptedAt().isEmpty();
            var stored = untouched ? store.read(account, fund).usableComposition()
                            : Optional.<CompositionValidationResult>empty();
            if (stored.isPresent())
            {
                var next = stored.get().source().provenance().retrievedAt().plus(Duration.ofHours(24));
                if (next.isAfter(clock.instant()))
                {
                    restoreEvidence(account, fund);
                    return CompletableFuture.completedFuture(
                                    new Refresh(Disposition.CADENCE, read(account, fund), Optional.of(next)));
                }
            }
            // The same applies to a stored answer without composition, such
            // as for a directly held share.
            var absent = untouched && stored.isEmpty() ? storedNoComposition(account, fund)
                            : Optional.<NoComposition>empty();
            if (absent.isPresent() && absent.get().storedAt().plus(Duration.ofHours(24)).isAfter(clock.instant()))
            {
                classifyHeld(account, fund);
                return CompletableFuture.completedFuture(
                                new Refresh(Disposition.NO_COMPOSITION, read(account, fund), Optional.empty()));
            }
        }

        var ticket = store.beginRefresh(account, fund);
        // Captured before the request, so an answer arriving after a
        // withdrawal of local storage is not written.
        var generation = store.generation(account);
        return coordinator.refresh(account, fund, manual).thenApply(refresh -> {
            if (refresh.disposition() == Disposition.COMPLETED
                            || refresh.disposition() == Disposition.NO_COMPOSITION)
                classifyHeld(account, fund);
            var status = refresh.snapshot().latestAttempt().map(CompositionResult::status);
            if (refresh.disposition() == Disposition.COMPLETED && status.isPresent()
                            && (status.get() == CompositionResult.Status.UNAVAILABLE
                                            || status.get() == CompositionResult.Status.UNSUPPORTED))
            {
                store.writeNoComposition(account, fund.identifiers(), status.get(), generation);
                synchronized (evidenceCache)
                {
                    noCompositions.remove(new Key(account, fund.identifiers()));
                }
            }
            // Deferred reads are not new attempts and must not rewrite the store.
            if (refresh.disposition() == Disposition.COMPLETED)
                refresh.snapshot().latestAttempt().ifPresent(attempt -> store.complete(ticket, attempt));
            return new Refresh(refresh.disposition(), read(account, fund), refresh.retryAt());
        });
    }

    /**
     * Classifies the held instrument itself (fund, stock or bond) so a direct
     * holding resolves to an issuer, not to an unresolved row.
     */
    private void classifyHeld(ProviderAccount account, CompositionInstrument fund)
    {
        try
        {
            // A stored answer needs no provider request and no budget.
            if (restoreEvidence(account, fund))
            {
                classifyCached(account, fund, false, new AtomicInteger());
            }
            else if (budget.acquirePermit(account, classificationCost))
            {
                var requests = new AtomicInteger();
                try
                {
                    classifyCached(account, fund, false, requests);
                }
                finally
                {
                    release(account, requests);
                }
            }
        }
        catch (IOException e)
        {
            // A failed classification does not discard the composition or
            // make the refresh fail; it remains uncached for the next pass.
            budget.record(account, e instanceof CompositionRefreshCoordinator.EnrichmentFailure failure
                            ? failure.result() : new CompositionResult(CompositionResult.Status.FAILED,
                                            Optional.empty(), Optional.empty()));
        }
    }

    /**
     * Enables company enrichment with this session's provider budget, so
     * profile requests and composition requests share one allowance. Profile
     * requests run only while a key is configured for the account.
     */
    public CompanyEnrichmentService enableEnrichment(ProviderAccount account, CompanyEnrichmentService.ProfileSource profiles,
                    CompanyEnrichmentService.Register register, BooleanSupplier registerEnabled,
                    CompanyEnrichmentCache cache, Executor enrichmentExecutor)
    {
        return enableEnrichment(account, profiles, null, register, registerEnabled, cache, enrichmentExecutor);
    }

    public CompanyEnrichmentService enableEnrichment(ProviderAccount account, CompanyEnrichmentService.ProfileSource profiles,
                    CompanyEnrichmentService.RevenueSource revenue, CompanyEnrichmentService.Register register,
                    BooleanSupplier registerEnabled, CompanyEnrichmentCache cache, Executor enrichmentExecutor)
    {
        return enableEnrichment(account, profiles, revenue, register, registerEnabled, cache, enrichmentExecutor,
                        (task, delay) -> {
                            // No automatic retry; the next result continues.
                        });
    }

    /**
     * @param enrichmentExecutor
     *            runs the provider and the register lane; needs two threads
     *            for them to run side by side
     * @param retries
     *            runs retries after a backoff or pause
     */
    public CompanyEnrichmentService enableEnrichment(ProviderAccount account, CompanyEnrichmentService.ProfileSource profiles,
                    CompanyEnrichmentService.RevenueSource revenue, CompanyEnrichmentService.Register register,
                    BooleanSupplier registerEnabled, CompanyEnrichmentCache cache, Executor enrichmentExecutor,
                    CompanyEnrichmentService.Scheduler retries)
    {
        // The credential state is read on the enrichment lane only, never
        // for a snapshot on the user interface thread.
        var service = new CompanyEnrichmentService(account, profiles, revenue, () -> {
            var mode = credentials.state(account).mode();
            return mode == Mode.CONFIGURED;
        }, register, registerEnabled, cache, budget, clock, enrichmentExecutor, retries);
        service.addListener(this::notifyEnrichmentListeners);
        companies = cache;
        enrichment = service;
        return service;
    }

    /**
     * Calls the listener on a background thread whenever company attributes
     * changed. Returns the action that removes the listener.
     */
    public Runnable addEnrichmentListener(Runnable listener)
    {
        enrichmentListeners.add(listener);
        return () -> enrichmentListeners.remove(listener);
    }

    private void notifyEnrichmentListeners()
    {
        for (var listener : enrichmentListeners)
            listener.run();
    }

    /**
     * Schedules the enrichment of a result's companies in the background. It
     * neither blocks nor changes the calculation; companies with fresh cached
     * attributes cause no request.
     */
    public void enrich(List<LookThroughResult.Company> companies)
    {
        var service = enrichment;
        if (service != null)
            service.schedule(companies);
    }

    /** Stops the running enrichment; answers so far are kept. */
    public void stopEnrichment()
    {
        var service = enrichment;
        if (service != null)
            service.stop();
    }

    /** Company attributes and pending count of the latest scheduled result; memory-only. */
    public CompanyEnrichmentService.Snapshot companyAttributes()
    {
        var service = enrichment;
        return service == null ? CompanyEnrichmentService.Snapshot.empty() : service.snapshot();
    }

    /** Cached classification of held instruments and constituents, exposed to the analysis. */
    public List<InstrumentEvidence> classifications()
    {
        synchronized (evidenceCache)
        {
            return evidenceCache.values().stream().filter(CompletableFuture::isDone)
                            .filter(future -> !future.isCompletedExceptionally())
                            .flatMap(future -> future.join().stream()).toList();
        }
    }
}
