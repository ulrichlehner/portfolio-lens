package io.portfoliolens.page;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.portfoliolens.core.CompanyEnrichmentService;
import io.portfoliolens.core.FundOverlap;
import io.portfoliolens.core.LookThroughResult;
import io.portfoliolens.core.Money;

/**
 * Everything the page shows, collected by one run. It holds no token, key or file path, so
 * nothing secret can reach the page by accident.
 */
public record PageInput(Instant generatedAt, ZoneId zone, String fileLabel, LookThroughResult result,
                CompanyEnrichmentService.Snapshot attributes, LookThroughDataStatus.Model dataStatus,
                Optional<LookThroughBanner.Kind> banner, ProviderState provider, FundOverlap overlap, Money accountCash,
                List<String> unsupportedHoldingTypes, int pendingLookups, Optional<Instant> nextAttempt, String version)
{
    /** Whether provider data is used, and if not, why. */
    public enum ProviderState
    {
        /** No provider key is configured. */
        NO_KEY,
        /** A key is configured, but local storage is not confirmed (design D6). */
        UNCONFIRMED,
        /** Provider data is requested and stored. */
        ENABLED
    }

    public PageInput
    {
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(zone);
        Objects.requireNonNull(fileLabel);
        Objects.requireNonNull(result);
        Objects.requireNonNull(attributes);
        Objects.requireNonNull(dataStatus);
        Objects.requireNonNull(banner);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(overlap);
        Objects.requireNonNull(accountCash);
        unsupportedHoldingTypes = List.copyOf(unsupportedHoldingTypes);
        Objects.requireNonNull(nextAttempt);
        Objects.requireNonNull(version);
    }
}
