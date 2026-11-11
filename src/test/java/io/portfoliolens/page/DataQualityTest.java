package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.InstrumentEvidence.AssetType;

/** Synthetic-only tests of the data quality warning. */
@SuppressWarnings("nls")
public class DataQualityTest
{
    private final PageFixture fixture = new PageFixture();

    @Test
    public void testNothingToReportWithoutProblems()
    {
        fixture.hold("company", "Synthetic company holding", AssetType.EQUITY, "SYNTH-CO", 1, 100);

        var quality = DataQuality.of(List.of(), fixture.model(fixture.result()));

        assertFalse(quality.attention());
        assertEquals(Optional.empty(), quality.problemText());
    }

    @Test
    public void testFailedLookupRaisesAttention()
    {
        var fund = fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);
        fixture.composition("fund", fixture.row(fixture.instrument("co", AssetType.EQUITY, "SYNTH-CO"), "1"));

        var quality = DataQuality.of(List.of(PageFixture.failed(fund)), fixture.model(fixture.result()));

        assertTrue(quality.attention());
        assertEquals("Lookup failed for 1 fund", quality.problemText().orElseThrow());
    }

    @Test
    public void testRejectedCompositionRaisesAttention()
    {
        var fund = fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);
        fixture.composition("fund", fixture.row(fixture.instrument("co", AssetType.EQUITY, "SYNTH-CO"), "1"));

        var quality = DataQuality.of(List.of(PageFixture.rejected(fund)), fixture.model(fixture.result()));

        assertTrue(quality.attention());
        assertEquals("Composition rejected for 1 fund", quality.problemText().orElseThrow());
    }

    @Test
    public void testFundWithoutCompositionRaisesAttention()
    {
        fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);

        // no rows: funds that are not looked up count as well
        var quality = DataQuality.of(List.of(), fixture.model(fixture.result()));

        assertTrue(quality.attention());
        assertEquals("1 fund without composition", quality.problemText().orElseThrow());
    }

    @Test
    public void testSecurityWithoutValuationRaisesAttention()
    {
        fixture.hold("company", "Synthetic company holding", AssetType.EQUITY, "SYNTH-CO", 1, 100);
        fixture.holdWithoutPrice("unpriced", "Synthetic unpriced holding", AssetType.EQUITY, "SYNTH-UP", 1);

        var quality = DataQuality.of(List.of(), fixture.model(fixture.result()));

        assertTrue(quality.attention());
        assertEquals(Texts.LensQualityNoValuation, quality.problemText().orElseThrow());
    }

    @Test
    public void testUnresolvedValueRaisesAttention()
    {
        fixture.hold("company", "Synthetic company holding", AssetType.EQUITY, "SYNTH-CO", 1, 99.5);
        // a direct holding without classification stays unresolved
        fixture.hold("unknown", "Synthetic unknown holding", null, null, 1, 0.5);

        var quality = DataQuality.of(List.of(), fixture.model(fixture.result()));

        assertTrue(quality.attention());
        assertEquals(MessageFormat.format(Texts.LensQualityUnresolved, "0.50%"), quality.problemText().orElseThrow());
    }

    @Test
    public void testFirstProblemWinsInTheDocumentedOrder()
    {
        var fund = fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);
        fixture.holdWithoutPrice("unpriced", "Synthetic unpriced holding", AssetType.EQUITY, "SYNTH-UP", 1);

        var model = fixture.model(fixture.result());

        // a failed lookup, a fund without composition and a missing valuation: the lookup comes first
        assertEquals("Lookup failed for 1 fund",
                        DataQuality.of(List.of(PageFixture.failed(fund)), model).problemText().orElseThrow());
        // without a row, the fund without composition comes before the missing valuation
        assertEquals("1 fund without composition", DataQuality.of(List.of(), model).problemText().orElseThrow());
    }

    @Test
    public void testUnvalidatedWeightsAloneDoNotRaiseAttention()
    {
        var fund = fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);
        fixture.unverifiedComposition("fund", fixture.row(fixture.instrument("co", AssetType.EQUITY, "SYNTH-CO"), "1"));

        var model = fixture.model(fixture.result());
        var quality = DataQuality.of(List.of(PageFixture.withData(fund)), model);

        assertTrue(model.unverifiedNotice().isPresent());
        assertFalse(quality.attention());
        assertTrue(quality.problemText().isEmpty());
    }
}
