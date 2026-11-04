package io.portfoliolens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.CompositionRefreshCoordinator.Attempt;
import io.portfoliolens.core.CompositionRefreshCoordinator.Disposition;
import io.portfoliolens.provider.FundCompositionProvider;

/** Budget and attempt state carried from one process to the next, as the job does between runs. */
public class PersistedAdmissionStateTest
{
    private static final Instant START = Instant.parse("2026-03-01T08:00:00Z");
    private final ProviderAccount account = new ProviderAccount("synthetic", "first");

    private static CompositionRequestBudget budget(Instant now)
    {
        return new CompositionRequestBudget("synthetic", Clock.fixed(now, ZoneOffset.UTC), 10, Duration.ofHours(24),
                        Duration.ofMinutes(15));
    }

    @Test
    public void testRestoredBudgetRefusesBeyondCapacityWithinWindow()
    {
        var first = budget(START);
        assertTrue(first.acquirePermit(account, 8));
        var later = budget(START.plus(Duration.ofHours(3)));
        later.restore(first.snapshot());
        assertFalse(later.acquirePermit(account, 3));
        assertTrue(later.acquirePermit(account, 2));
    }

    @Test
    public void testRestoredChargesExpireAfterWindow()
    {
        var first = budget(START);
        assertTrue(first.acquirePermit(account, 10));
        var nextDay = budget(START.plus(Duration.ofHours(25)));
        nextDay.restore(first.snapshot());
        assertTrue(nextDay.acquirePermit(account, 10));
        assertTrue(nextDay.snapshot().charges().stream().allMatch(c -> c.at().isAfter(START)));
    }

    @Test
    public void testRestoredRetryDeadlineDefersRequests()
    {
        var first = budget(START);
        first.record(account, new CompositionResult(CompositionResult.Status.RATE_LIMITED, Optional.empty(),
                        Optional.of(START.plus(Duration.ofMinutes(30)))));
        var later = budget(START.plus(Duration.ofMinutes(5)));
        later.restore(first.snapshot());
        assertFalse(later.acquirePermit(account, 1));
        assertEquals(Optional.of(START.plus(Duration.ofMinutes(30))), later.nextAvailable(account, 1));
        var after = budget(START.plus(Duration.ofMinutes(31)));
        after.restore(first.snapshot());
        assertTrue(after.acquirePermit(account, 1));
    }

    @Test
    public void testRestoredSuspensionLiftsOnlyWithNewConfiguration()
    {
        var first = budget(START);
        first.configurationChanged(account, 7);
        first.record(account, new CompositionResult(CompositionResult.Status.AUTHENTICATION_FAILED, Optional.empty(),
                        Optional.empty()));
        var later = budget(START.plus(Duration.ofHours(1)));
        later.restore(first.snapshot());
        assertTrue(later.isSuspended(account));
        assertFalse(later.configurationChanged(account, 7));
        assertTrue(later.isSuspended(account));
        assertTrue(later.configurationChanged(account, 8));
        assertFalse(later.isSuspended(account));
    }

    @Test
    public void testRestoredAttemptKeepsCadenceAcrossProcesses()
    {
        var now = START.plus(Duration.ofHours(2));
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var provider = new FundCompositionProvider()
        {
            @Override
            public String getId()
            {
                return "synthetic";
            }

            @Override
            public CompositionPersistencePolicy getPersistencePolicy(ProviderAccount requested)
            {
                throw new AssertionError();
            }

            @Override
            public CompositionResult getComposition(ProviderAccount requested, CompositionInstrument fund)
            {
                throw new AssertionError("A fund within its cadence must not be requested");
            }
        };
        var coordinator = new CompositionRefreshCoordinator(provider, budget(now), clock, Runnable::run, 1, 1, 0, 1,
                        (requested, source) -> List.of());
        var fund = new CompositionInstrument("Synthetic fund", Map.of("isin", "XX0000000017"));
        coordinator.restore(account, new Attempt(fund.identifiers(), START, Instant.MIN, false));

        var refresh = coordinator.refresh(account, fund, false).join();

        assertEquals(Disposition.CADENCE, refresh.disposition());
        assertEquals(Optional.of(START.plus(Duration.ofHours(24))), refresh.retryAt());
        assertEquals(List.of(new Attempt(fund.identifiers(), START, Instant.MIN, false)),
                        coordinator.attempts(account));
    }
}
