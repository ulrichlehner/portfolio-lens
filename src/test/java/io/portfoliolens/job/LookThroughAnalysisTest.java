package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.HeldInstrument;
import io.portfoliolens.core.SecuritiesValuation;
import io.portfoliolens.testing.SyntheticHoldings;
import io.portfoliolens.core.Money;
import io.portfoliolens.page.Values;
import io.portfoliolens.core.CompositionInstrument;
import io.portfoliolens.core.CompositionProvenance;
import io.portfoliolens.core.CompositionRefreshCoordinator.Snapshot;
import io.portfoliolens.core.CompositionResult;
import io.portfoliolens.core.FundComposition;
import io.portfoliolens.core.InstrumentEvidence;
import io.portfoliolens.core.InstrumentEvidence.Assertion;
import io.portfoliolens.core.InstrumentEvidence.AssetType;
import io.portfoliolens.core.InstrumentEvidence.Identifier;
import io.portfoliolens.core.InstrumentEvidence.Provenance;
import io.portfoliolens.core.InstrumentResolution;
import io.portfoliolens.core.InstrumentResolver;
import io.portfoliolens.core.LookThroughContribution.Category;
import io.portfoliolens.core.LookThroughContribution.Reason;
import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.page.LookThroughViewPresenter;
import io.portfoliolens.page.Texts;

@SuppressWarnings("nls")
public class LookThroughAnalysisTest
{
    private static final ProviderAccount ACCOUNT = new ProviderAccount("synthetic", "test");
    private static final Instant NOW = Instant.parse("2026-01-31T12:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-01-31");
    private final SyntheticHoldings holdings = new SyntheticHoldings();

    private HeldInstrument holding(String id, int shares)
    {
        return holdings.hold("Synthetic " + id, id, shares, 10);
    }

    private SecuritiesValuation valuation(HeldInstrument... selected)
    {
        return holdings.valuation(DATE, List.of(selected));
    }

    private InstrumentResolution resolution(CompositionInstrument instrument, AssetType type)
    {
        var provenance = new Provenance(ACCOUNT, "synthetic-evidence", NOW);
        var evidence = new InstrumentEvidence(instrument.identifiers(),
                        new Assertion<>(new Identifier("synthetic", instrument.identifiers().get("ISIN")), provenance),
                        Optional.of(new Assertion<>(type, provenance)),
                        type == AssetType.EQUITY
                                        ? Optional.of(new Assertion<>(new Identifier("synthetic-company", "company"),
                                                        provenance))
                                        : Optional.empty());
        return new InstrumentResolver().resolve(instrument, List.of(evidence), ignored -> List.of(), () -> false);
    }

    @Test
    public void testCurrentSelectionProducesResultBeforeAnyProviderResponse()
    {
        var selected = holding("SYNTHETIC-ONE", 6);
        holding("SYNTHETIC-UNSELECTED", 20);
        var result = new LookThroughAnalysis().calculate(valuation(selected), List.of(), List.of());
        assertEquals(Money.of("EUR", 6000), result.denominator().orElseThrow());
        assertEquals(0, result.value(Category.UNRESOLVED).compareTo(new BigDecimal("6000")));
        assertEquals(1, result.contributions().size());
        assertTrue(result.companies().isEmpty());
    }

    @Test
    public void testRetainedEvidenceCombinesDirectAndIndirectExposureWithoutNormalizing()
    {
        var share = holding("SYNTHETIC-SHARE", 6);
        var fund = holding("SYNTHETIC-FUND", 4);
        var child = share.asCompositionInstrument();
        var parent = fund.asCompositionInstrument();
        var composition = new FundComposition(new CompositionProvenance(ACCOUNT, parent, NOW, Optional.empty(),
                        Optional.empty()), FundComposition.Denominator.TOTAL_FUND_NAV,
                        FundComposition.ExposureBasis.PHYSICAL_HOLDINGS, FundComposition.Completeness.PARTIAL,
                        List.of(new FundComposition.Constituent(child, Optional.of("0.5"))));
        var retained = new Snapshot(Optional.of(NOW), Optional.empty(), Optional.of(composition),
                        Optional.empty(), List.of(resolution(child, AssetType.EQUITY)));
        var result = new LookThroughAnalysis().calculate(valuation(share, fund), List.of(retained),
                        List.of());
        assertEquals(Money.of("EUR", 10000), result.denominator().orElseThrow());
        var company = result.companies().getFirst();
        assertEquals(0, company.direct().compareTo(new BigDecimal("6000")));
        assertEquals(0, company.indirect().compareTo(new BigDecimal("2000")));
        assertEquals(0, result.value(Category.UNRESOLVED).compareTo(new BigDecimal("2000")));
        assertEquals(0, result.total().compareTo(new BigDecimal("10000")));
    }

    @Test
    public void testUnverifiedCompositionExpandsIntoClassifiedConstituents()
    {
        var fund = holding("SYNTHETIC-FUND", 4);
        var parent = fund.asCompositionInstrument();
        var child = new CompositionInstrument("Synthetic position", java.util.Map.of("ISIN", "SYNTHETIC-CHILD"));
        var composition = new FundComposition(new CompositionProvenance(ACCOUNT, parent, NOW, Optional.empty(),
                        Optional.empty()), FundComposition.Denominator.UNKNOWN,
                        FundComposition.ExposureBasis.UNKNOWN, FundComposition.Completeness.UNKNOWN,
                        List.of(new FundComposition.Constituent(child, Optional.of("1"))));
        var retained = new Snapshot(Optional.of(NOW), Optional.empty(), Optional.empty(),
                        Optional.of(composition), List.of(resolution(child, AssetType.EQUITY)));
        var result = new LookThroughAnalysis().calculate(valuation(fund), List.of(retained),
                        List.of());
        assertEquals(0, result.value(Category.UNVERIFIED_HOLDING).compareTo(new BigDecimal("0")));
        assertEquals(0, result.value(Category.UNRESOLVED).compareTo(new BigDecimal("0")));
        assertEquals(1, result.companies().size());
        assertEquals(0, result.companies().getFirst().indirect().compareTo(new BigDecimal("4000")));
    }

    @Test
    public void testChangedPricesRecalculateWithoutStoredDerivedResults()
    {
        var security = holding("SYNTHETIC-SHARE", 6);
        var first = new LookThroughAnalysis().calculate(valuation(security), List.of(), List.of());
        holdings.setPrice(security, 20);
        var second = new LookThroughAnalysis().calculate(valuation(security), List.of(), List.of());
        assertEquals(Money.of("EUR", 6000), first.denominator().orElseThrow());
        assertEquals(Money.of("EUR", 12000), second.denominator().orElseThrow());
    }

    @Test
    public void testInvalidCompositionIsNotReportedAsMissingClassification()
    {
        var fund = holding("SYNTHETIC-FUND", 4);
        var parent = fund.asCompositionInstrument();
        var composition = new FundComposition(new CompositionProvenance(ACCOUNT, parent, NOW, Optional.empty(),
                        Optional.empty()), FundComposition.Denominator.UNKNOWN,
                        FundComposition.ExposureBasis.UNKNOWN, FundComposition.Completeness.UNKNOWN,
                        List.of(new FundComposition.Constituent(parent, Optional.of("-0.1"))));
        var retained = new Snapshot(Optional.of(NOW), Optional.of(new CompositionResult(
                        CompositionResult.Status.RECEIVED, Optional.of(composition), Optional.empty())),
                        Optional.empty(), Optional.empty(), List.of());
        var result = new LookThroughAnalysis().calculate(valuation(fund), List.of(retained),
                        List.of());
        assertEquals(Reason.UNUSABLE_COMPOSITION, result.contributions().getFirst().reason());
        assertEquals(0, result.value(Category.UNRESOLVED).compareTo(new BigDecimal("4000")));
    }

    @Test
    public void testIdentifierlessPositionIsNotInventedAsCash()
    {
        var fund = holding("SYNTHETIC-FUND", 4);
        var parent = fund.asCompositionInstrument();
        var child = new CompositionInstrument("Synthetic unknown asset", java.util.Map.of());
        var composition = new FundComposition(new CompositionProvenance(ACCOUNT, parent, NOW, Optional.empty(),
                        Optional.empty()), FundComposition.Denominator.UNKNOWN,
                        FundComposition.ExposureBasis.UNKNOWN, FundComposition.Completeness.UNKNOWN,
                        List.of(new FundComposition.Constituent(child, Optional.of("1"))));
        var retained = new Snapshot(Optional.of(NOW), Optional.empty(), Optional.empty(), Optional.of(composition),
                        List.of());
        var result = new LookThroughAnalysis().calculate(valuation(fund), List.of(retained),
                        List.of());
        assertEquals(0, result.value(Category.UNRESOLVED).compareTo(new BigDecimal("4000")));
        assertEquals(0, result.value(Category.CASH_OR_OTHER).signum());
    }

    private Snapshot unverifiedFund(HeldInstrument fund, CompositionInstrument child, InstrumentResolution lookup)
    {
        var parent = fund.asCompositionInstrument();
        var composition = new FundComposition(new CompositionProvenance(ACCOUNT, parent, NOW, Optional.empty(),
                        Optional.empty()), FundComposition.Denominator.UNKNOWN,
                        FundComposition.ExposureBasis.UNKNOWN, FundComposition.Completeness.UNKNOWN,
                        List.of(new FundComposition.Constituent(child, Optional.of("1"))));
        return new Snapshot(Optional.of(NOW), Optional.empty(), Optional.empty(), Optional.of(composition),
                        List.of(lookup));
    }

    @Test
    public void testAnsweredEmptyLookupOfListedShareIsAssumedCompany()
    {
        var fund = holding("SYNTHETIC-FUND", 4);
        var child = new CompositionInstrument("Synthetic Holding Corp", java.util.Map.of("ISIN", "ZZ0000000019"));
        var lookup = new InstrumentResolver().resolve(child, List.of(), ignored -> List.of(), () -> true);
        assertEquals(InstrumentResolution.Reason.ASSUMED_COMPANY, lookup.reason());
        var result = new LookThroughAnalysis().calculate(valuation(fund),
                        List.of(unverifiedFund(fund, child, lookup)), List.of());
        assertEquals(1, result.companies().size());
        assertEquals(0, result.value(Category.UNRESOLVED).signum());
        assertTrue(LookThroughAnalysis.diagnostics(result).contains("assumed=1"));
    }

    @Test
    public void testFailedLookupStaysUnresolvedAndKeepsItsStatus()
    {
        var fund = holding("SYNTHETIC-FUND", 4);
        var child = new CompositionInstrument("Synthetic Holding Corp", java.util.Map.of("ISIN", "ZZ0000000019"));
        var lookup = new InstrumentResolver().resolve(child, List.of(), ignored -> {
            throw new java.io.IOException();
        }, () -> true);
        var result = new LookThroughAnalysis().calculate(valuation(fund),
                        List.of(unverifiedFund(fund, child, lookup)), List.of());
        assertTrue(result.companies().isEmpty());
        var resolution = result.contributions().getFirst().resolution().orElseThrow();
        assertEquals(InstrumentResolution.LookupStatus.FAILED, resolution.lookupStatus());
        var line = LookThroughAnalysis.diagnostics(result);
        assertTrue( line.contains("resolved=0, assumed=0"), line);
        assertTrue( line.contains("{MISSING_IDENTITY=1}"), line);
        assertTrue( line.contains("{FAILED=1}"), line);
        assertTrue( !line.contains("ZZ00") && !line.contains("Synthetic"), line);
        assertEquals(Texts.LookThroughReasonLookupFailed,
                        LookThroughViewPresenter.lookupText(resolution, Texts.LookThroughReasonMissingIdentity));
    }

    @Test
    public void testReasonTextsDistinguishLookupOutcomes()
    {
        var company = new CompositionInstrument("Synthetic Holding Corp", java.util.Map.of("ISIN", "ZZ0000000019"));
        var fundLike = new CompositionInstrument("Synthetic Money Market Fund", java.util.Map.of("ISIN", "ZZ0000000029"));
        var resolver = new InstrumentResolver();
        var missing = Texts.LookThroughReasonMissingIdentity;
        assertEquals(Texts.LookThroughReasonLookupPending, LookThroughViewPresenter.lookupText(
                        resolver.resolve(company, List.of(), ignored -> List.of(), () -> false), missing));
        assertEquals(Texts.LookThroughReasonNameNotCompany, LookThroughViewPresenter.lookupText(
                        resolver.resolve(fundLike, List.of(), ignored -> List.of(), () -> true), missing));
        assertEquals(Texts.LookThroughReasonNoClassificationFound, LookThroughViewPresenter.lookupText(
                        resolver.resolve(new CompositionInstrument("Synthetic Ticker Only", java.util.Map.of("symbol", "SYN")),
                                        List.of(), ignored -> List.of(), () -> true),
                        missing));
    }
}
