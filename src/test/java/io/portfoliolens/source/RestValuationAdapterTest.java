package io.portfoliolens.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.Money;
import io.portfoliolens.core.SecuritiesValuation.Reason;
import io.portfoliolens.source.PpModel.Holding;
import io.portfoliolens.source.PpModel.Holdings;
import io.portfoliolens.source.PpModel.Instrument;
import io.portfoliolens.source.PpModel.MonetaryValue;
import io.portfoliolens.source.PpModel.Price;
import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.http.JdkHttpGet;

/**
 * Valuation from the PP holdings report (design D3). The PP-side cases of the fork's
 * SecuritiesValuationAdapterTest that still apply are covered here; the others concern PP's own
 * snapshot, transaction price fallback and exchange rate series, which Lens no longer computes.
 */
public class RestValuationAdapterTest
{
    private static final LocalDate DATE = LocalDate.of(2026, 7, 20);

    private static RestValuationAdapter.Scope fixture(String holdings, String instruments) throws Exception
    {
        try (var server = new FakePpServer("t").serve("/v1/files/main/holdings", holdings)
                        .serve("/v1/files/main/instruments", instruments))
        {
            var client = new PpClient(server.baseUrl(), "t", new JdkHttpGet("t"));
            return new RestValuationAdapter().value(client.holdings("main", Optional.empty()),
                            client.instruments("main"));
        }
    }

    private static Holding holding(String uuid, String shares, String price, String priceCurrency, String value,
                    String local)
    {
        return new Holding("instrument", uuid, "Synthetic " + uuid, Optional.of(new BigDecimal(shares)),
                        price == null ? Optional.empty()
                                        : Optional.of(new Price(new BigDecimal(price), priceCurrency, Optional.of(DATE))),
                        new MonetaryValue(new BigDecimal(value), "EUR"), BigDecimal.ZERO,
                        local == null ? Optional.empty() : Optional.of(new MonetaryValue(new BigDecimal(local), priceCurrency)));
    }

    private static RestValuationAdapter.Scope value(Holding... items)
    {
        return new RestValuationAdapter().value(new Holdings(DATE, "EUR", new MonetaryValue(BigDecimal.ONE, "EUR"),
                        List.of(items)), List.of());
    }

    @Test
    public void testCashAndUnknownTypesAreOutsideTheDenominator() throws Exception
    {
        var scope = fixture("pp/holdings.json", "pp/instruments.json");
        var valuation = scope.valuation();
        assertEquals(5, valuation.positions().size());
        assertEquals(Money.of("EUR", 2284000), valuation.valuedSubset());
        assertEquals(Optional.of(Money.of("EUR", 2284000)), valuation.denominator());
        assertEquals(Money.of("EUR", 50050), scope.accountCash());
        assertEquals(List.of("crypto-wallet"), scope.unsupportedHoldingTypes());
        assertTrue(valuation.isEntireSelectionSupported());
    }

    @Test
    public void testConvertedHoldingEntersWithReportingCurrencyValue() throws Exception
    {
        var scope = fixture("pp/holdings.json", "pp/instruments.json");
        var us = scope.valuation().positions().stream().filter(p -> p.security().uuid().endsWith("02")).findFirst()
                        .orElseThrow();
        assertEquals(Money.of("EUR", 184000), us.value());
        assertEquals(Map.of("ISIN", "US0378331005", RestValuationAdapter.WKN, "865985", RestValuationAdapter.TICKER,
                        "AAPL"), us.security().identifiers());
        assertEquals(Map.of("ISIN", "US0378331005"), us.security().asCompositionInstrument().identifiers());
    }

    @Test
    public void testEdgeCasesBecomeDiagnostics() throws Exception
    {
        var scope = fixture("pp/holdings-edge.json", "pp/instruments-edge.json");
        var reasons = scope.valuation().diagnostics().stream()
                        .collect(Collectors.toMap(d -> d.security().name(), d -> d.reason()));
        assertEquals(Map.of("Synthetic Short", Reason.UNSUPPORTED_NEGATIVE_POSITION, //
                        "Synthetic Without Price", Reason.MISSING_VALUATION, //
                        "Synthetic One To One", Reason.MISSING_CONVERSION, //
                        "Synthetic Without Local", Reason.MISSING_CONVERSION, //
                        "Synthetic Zero Price", Reason.MISSING_VALUATION), reasons);
        assertEquals(1, scope.valuation().positions().size());
        assertEquals(Money.of("EUR", 100000), scope.valuation().positions().get(0).value());
        // whole-portfolio percentages are withheld
        assertFalse(scope.valuation().isValuationComplete());
        assertEquals(Optional.empty(), scope.valuation().denominator());
    }

    @Test
    public void testSilentOneToOneConversionIsMissingConversion()
    {
        var scope = value(holding("a", "10", "110.5", "USD", "1105", "1105.00"));
        assertEquals(Reason.MISSING_CONVERSION, scope.valuation().diagnostics().get(0).reason());
    }

    @Test
    public void testShortIsExcludedWithoutReducingPositiveDenominator()
    {
        var scope = value(holding("long", "10", "100", "EUR", "1000", null),
                        holding("short", "-3", "100", "EUR", "-300", null));
        assertEquals(Optional.of(Money.of("EUR", 100000)), scope.valuation().denominator());
        assertFalse(scope.valuation().isEntireSelectionSupported());
        assertTrue(scope.valuation().isValuationComplete());
    }

    @Test
    public void testEmptySelectionHasNoPercentageDenominator() throws Exception
    {
        var scope = fixture("pp/holdings-empty.json", "pp/instruments.json");
        assertTrue(scope.valuation().positions().isEmpty());
        assertEquals(Optional.empty(), scope.valuation().denominator());
    }

    @Test
    public void testAmountsRoundHalfEvenToHundredths()
    {
        var scope = value(holding("a", "3", "33.3333", "EUR", "100.005", null),
                        holding("b", "1", "0.001", "EUR", "0.004", null));
        assertEquals(Money.of("EUR", 10000), scope.valuation().positions().get(0).value());
        // worth less than one hundredth: a valuation gap
        assertEquals(Reason.MISSING_VALUATION, scope.valuation().diagnostics().get(0).reason());
    }

    @Test
    public void testInstrumentWithoutMasterDataKeepsHoldingName()
    {
        var scope = new RestValuationAdapter().value(new Holdings(DATE, "EUR", new MonetaryValue(BigDecimal.ONE, "EUR"),
                        List.of(holding("x", "1", "10", "EUR", "10", null))),
                        List.of(new Instrument("other", "Other", Optional.of("EUR"), Optional.empty(), Optional.empty(),
                                        Optional.empty())));
        assertEquals("Synthetic x", scope.instruments().get(0).name());
        assertTrue(scope.instruments().get(0).identifiers().isEmpty());
    }
}
