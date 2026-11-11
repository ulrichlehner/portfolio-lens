package io.portfoliolens.page;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.portfoliolens.core.CompositionInstrument;
import io.portfoliolens.core.CompositionPersistencePolicy;
import io.portfoliolens.core.CompositionProvenance;
import io.portfoliolens.core.CompositionResult;
import io.portfoliolens.core.CompositionStore;
import io.portfoliolens.core.FundComposition;
import io.portfoliolens.core.FundComposition.Completeness;
import io.portfoliolens.core.FundComposition.Constituent;
import io.portfoliolens.core.FundComposition.Denominator;
import io.portfoliolens.core.FundComposition.ExposureBasis;
import io.portfoliolens.core.HeldInstrument;
import io.portfoliolens.core.InstrumentEvidence;
import io.portfoliolens.core.InstrumentEvidence.Assertion;
import io.portfoliolens.core.InstrumentEvidence.AssetType;
import io.portfoliolens.core.InstrumentEvidence.Identifier;
import io.portfoliolens.core.InstrumentEvidence.Provenance;
import io.portfoliolens.core.InstrumentResolution;
import io.portfoliolens.core.InstrumentResolver;
import io.portfoliolens.core.LookThroughEngine;
import io.portfoliolens.core.LookThroughResult;
import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.page.LookThroughDataStatus.FundFacts;
import io.portfoliolens.page.LookThroughDataStatus.FundStatus;
import io.portfoliolens.page.LookThroughViewPresenter.Model;
import io.portfoliolens.testing.SyntheticHoldings;

/** Synthetic portfolio, look-through result and status rows for the page tests. */
@SuppressWarnings("nls")
final class PageFixture
{
    static final ProviderAccount SOURCE = new ProviderAccount("synthetic", "test");
    private static final Instant RETRIEVED = Instant.parse("2026-01-16T12:00:00Z");
    private static final LocalDate VALUATION_DATE = LocalDate.parse("2026-01-15");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-17T12:00:00Z"), ZoneId.of("UTC"));

    final SyntheticHoldings holdings = new SyntheticHoldings();
    private final Map<HeldInstrument, InstrumentResolution> owned = new HashMap<>();
    private final Map<CompositionInstrument, InstrumentResolution> resolutions = new HashMap<>();
    private final Map<Identifier, FundComposition> compositions = new HashMap<>();
    private final Map<Identifier, FundComposition> unverified = new HashMap<>();
    private final Map<String, CompositionInstrument> instruments = new HashMap<>();
    private final List<HeldInstrument> selected = new ArrayList<>();

    CompositionInstrument instrument(String id, AssetType type, String company)
    {
        var existing = instruments.get(id);
        if (existing != null)
            return existing;
        var instrument = new CompositionInstrument("Synthetic " + id, Map.of("synthetic:instrument", id));
        var provenance = new Provenance(SOURCE, "record-" + id, RETRIEVED);
        var evidence = new InstrumentEvidence(instrument.identifiers(),
                        new Assertion<>(new Identifier("synthetic:instrument", id), provenance),
                        Optional.ofNullable(type).map(value -> new Assertion<>(value, provenance)),
                        Optional.ofNullable(company).map(value -> new Assertion<>(
                                        new Identifier("synthetic:company", value), provenance)));
        resolutions.put(instrument, new InstrumentResolver().resolve(instrument, List.of(evidence),
                        ignored -> List.of(), () -> false));
        instruments.put(id, instrument);
        return instrument;
    }

    HeldInstrument hold(String id, String name, AssetType type, String company, double shares, double price)
    {
        var security = holdings.hold(name, "SYNTH-" + id, shares, price);
        owned.put(security, resolutions.get(instrument(id, type, company)));
        selected.add(security);
        return security;
    }

    HeldInstrument holdWithoutPrice(String id, String name, AssetType type, String company, double shares)
    {
        var security = holdings.holdWithoutPrice(name, "SYNTH-" + id, shares);
        owned.put(security, resolutions.get(instrument(id, type, company)));
        selected.add(security);
        return security;
    }

    Constituent row(CompositionInstrument instrument, String weight)
    {
        return new Constituent(instrument, Optional.of(weight));
    }

    /** A validated composition of a held fund. */
    void composition(String fundId, Constituent... rows)
    {
        var fund = instruments.get(fundId);
        var provenance = new CompositionProvenance(SOURCE, fund, RETRIEVED, Optional.empty(), Optional.empty());
        compositions.put(resolutions.get(fund).instrument().orElseThrow(), new FundComposition(provenance,
                        Denominator.TOTAL_FUND_NAV, ExposureBasis.PHYSICAL_HOLDINGS, Completeness.UNKNOWN,
                        List.of(rows)));
    }

    /** A composition whose weights are not validated. */
    void unverifiedComposition(String fundId, Constituent... rows)
    {
        var fund = instruments.get(fundId);
        var provenance = new CompositionProvenance(SOURCE, fund, RETRIEVED, Optional.empty(), Optional.empty());
        unverified.put(resolutions.get(fund).instrument().orElseThrow(), new FundComposition(provenance,
                        Denominator.UNKNOWN, ExposureBasis.UNKNOWN, Completeness.UNKNOWN, List.of(rows)));
    }

    LookThroughResult result()
    {
        var valuation = holdings.valuation(VALUATION_DATE, selected);
        return new LookThroughEngine().calculate(valuation, owned, resolutions, compositions, unverified);
    }

    Model model(LookThroughResult result)
    {
        return new LookThroughViewPresenter().present(result);
    }

    /** The instrument of a held security as the status rows reference it. */
    static CompositionInstrument statusInstrument(HeldInstrument security)
    {
        return security.asCompositionInstrument();
    }

    private static FundComposition usable(CompositionInstrument fund, String... weights)
    {
        var provenance = new CompositionProvenance(SOURCE, fund, RETRIEVED, Optional.empty(), Optional.empty());
        var rows = new ArrayList<Constituent>();
        for (int ii = 0; ii < weights.length; ii++)
            rows.add(new Constituent(new CompositionInstrument("Synthetic constituent " + ii,
                            Map.of("synthetic:instrument", "constituent-" + ii)), Optional.of(weights[ii])));
        return new FundComposition(provenance, Denominator.TOTAL_FUND_NAV, ExposureBasis.PHYSICAL_HOLDINGS,
                        Completeness.UNKNOWN, rows);
    }

    private static FundStatus status(FundFacts facts)
    {
        var global = new LookThroughDataStatus.GlobalFacts(CompositionStore.State.SESSION_ONLY,
                        new CompositionPersistencePolicy.EffectiveMode(
                                        CompositionPersistencePolicy.StorageMode.SESSION_ONLY,
                                        CompositionPersistencePolicy.Reason.POLICY_UNVERIFIED));
        return LookThroughDataStatus.present(CLOCK, global, List.of(facts)).funds().get(0);
    }

    /** A row with a usable composition. */
    static FundStatus withData(HeldInstrument security)
    {
        var fund = statusInstrument(security);
        return status(new FundFacts(fund, SOURCE, Optional.of(usable(fund, "0.5", "0.5")),
                        Optional.of(CompositionResult.Status.RECEIVED), Optional.empty(), false));
    }

    /** A row whose provider has no composition. */
    static FundStatus withoutComposition(HeldInstrument security)
    {
        return status(new FundFacts(statusInstrument(security), SOURCE, Optional.empty(),
                        Optional.of(CompositionResult.Status.UNAVAILABLE), Optional.empty(), false));
    }

    /** A row with a failed lookup and no data. */
    static FundStatus failed(HeldInstrument security)
    {
        return status(new FundFacts(statusInstrument(security), SOURCE, Optional.empty(),
                        Optional.of(CompositionResult.Status.FAILED), Optional.empty(), false));
    }

    /** A row whose received composition was rejected, because its weights add up to more than one. */
    static FundStatus rejected(HeldInstrument security)
    {
        var fund = statusInstrument(security);
        return status(new FundFacts(fund, SOURCE, Optional.empty(), Optional.of(CompositionResult.Status.RECEIVED),
                        Optional.empty(), false, Optional.of(usable(fund, "0.8", "0.8"))));
    }

    /** A row of a security that was never answered. */
    static FundStatus checking(HeldInstrument security)
    {
        return status(new FundFacts(statusInstrument(security), SOURCE, Optional.empty(), Optional.empty(),
                        Optional.empty(), false));
    }
}
