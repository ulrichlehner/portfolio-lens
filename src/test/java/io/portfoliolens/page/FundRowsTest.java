package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.InstrumentEvidence.AssetType;

/** Synthetic-only tests of the fund table rows. */
@SuppressWarnings("nls")
public class FundRowsTest
{
    private final PageFixture fixture = new PageFixture();

    @Test
    public void testFundsAreSortedByValueWithShareAndCompanies()
    {
        fixture.hold("company", "Synthetic direct company", AssetType.EQUITY, "SYNTH-CO", 1, 100);
        var small = fixture.hold("small", "Synthetic small fund", AssetType.FUND, null, 1, 40);
        var large = fixture.hold("large", "Synthetic large fund", AssetType.FUND, null, 1, 60);
        var first = fixture.instrument("first", AssetType.EQUITY, "SYNTH-A");
        var second = fixture.instrument("second", AssetType.EQUITY, "SYNTH-B");
        fixture.composition("large", fixture.row(first, "0.5"), fixture.row(second, "0.5"));
        fixture.composition("small", fixture.row(first, "1"));

        var result = fixture.result();
        var model = fixture.model(result);
        var rows = FundRows.funds(result, model, List.of(PageFixture.withData(small), PageFixture.withData(large)));

        assertEquals(2, rows.size());
        assertEquals("Synthetic large fund", rows.get(0).status().fundLabel());
        assertEquals("60.00", rows.get(0).valueText());
        assertEquals("30.00%", rows.get(0).shareText());
        assertEquals("2", rows.get(0).companiesText());
        assertEquals("Synthetic small fund", rows.get(1).status().fundLabel());
        assertEquals("40.00", rows.get(1).valueText());
        assertEquals("20.00%", rows.get(1).shareText());
        assertEquals("1", rows.get(1).companiesText());
        assertEquals("100.00%", rows.get(0).lookedThroughText());
    }

    @Test
    public void testFundWithoutCompositionShowsDashes()
    {
        fixture.hold("company", "Synthetic direct company", AssetType.EQUITY, "SYNTH-CO", 1, 100);
        var fund = fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);

        var result = fixture.result();
        var rows = FundRows.funds(result, fixture.model(result), List.of(PageFixture.withoutComposition(fund)));

        assertEquals(1, rows.size());
        assertEquals("50.00%", rows.get(0).shareText());
        assertEquals("–", rows.get(0).companiesText());
        assertEquals("–", rows.get(0).lookedThroughText());
    }

    @Test
    public void testSecurityOfUnknownClassificationIsNotAFund()
    {
        var fund = fixture.hold("fund", "Synthetic fund", AssetType.FUND, null, 1, 100);
        var unknown = fixture.hold("unknown", "Synthetic unknown holding", null, null, 1, 10);
        fixture.composition("fund", fixture.row(fixture.instrument("co", AssetType.EQUITY, "SYNTH-CO"), "1"));
        var rows = List.of(PageFixture.withData(fund), PageFixture.checking(unknown));

        var result = fixture.result();
        var funds = FundRows.funds(result, fixture.model(result), rows);
        var unclassified = FundRows.unclassified(result, rows);

        assertEquals(1, funds.size());
        assertEquals("Synthetic fund", funds.get(0).status().fundLabel());
        assertEquals(1, unclassified.size());
        assertEquals("Synthetic unknown holding", unclassified.get(0).fundLabel());
        assertTrue(funds.stream().noneMatch(row -> row.status().fundLabel().equals("Synthetic unknown holding")));
    }
}
