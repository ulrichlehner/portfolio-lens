package io.portfoliolens.source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.portfoliolens.core.HeldInstrument;
import io.portfoliolens.core.InstrumentEvidence;
import io.portfoliolens.core.Money;
import io.portfoliolens.core.SecuritiesValuation;
import io.portfoliolens.core.SecuritiesValuation.Diagnostic;
import io.portfoliolens.core.SecuritiesValuation.Position;
import io.portfoliolens.core.SecuritiesValuation.Reason;
import io.portfoliolens.source.PpModel.Holding;
import io.portfoliolens.source.PpModel.Holdings;
import io.portfoliolens.source.PpModel.Instrument;

/**
 * Turns the PP holdings report into the securities valuation of the calculation (design D3). PP
 * has already converted every holding into the reporting currency; this adapter only decides
 * which holdings are usable and discloses the others. A currency pair without an exchange rate is
 * converted 1:1 by PP without notice, so a valuation numerically equal to its differing local
 * valuation counts as a missing conversion.
 */
public final class RestValuationAdapter
{
    /** The analysed scope of one holdings report. */
    public record Scope(LocalDate date, String reportingCurrency, SecuritiesValuation valuation,
                    List<HeldInstrument> instruments, Money accountCash, List<String> unsupportedHoldingTypes)
    {
        public Scope
        {
            instruments = List.copyOf(instruments);
            unsupportedHoldingTypes = List.copyOf(unsupportedHoldingTypes);
        }
    }

    /** Identifier key of an ISIN in provider requests. */
    public static final String ISIN = InstrumentEvidence.ISIN;

    /** Display-only identifier keys; providers never receive them. */
    public static final String WKN = "display:wkn";
    public static final String TICKER = "display:ticker";

    public Scope value(Holdings holdings, List<Instrument> instruments)
    {
        var byUuid = new HashMap<String, Instrument>();
        instruments.forEach(instrument -> byUuid.putIfAbsent(instrument.uuid(), instrument));
        var currency = holdings.reportingCurrency();

        var positions = new ArrayList<Position>();
        var diagnostics = new ArrayList<Diagnostic>();
        var held = new ArrayList<HeldInstrument>();
        var unsupported = new ArrayList<String>();
        long total = 0;
        long cash = 0;

        for (var holding : holdings.items())
        {
            if (holding.isCashAccount())
            {
                cash += Money.ofDecimal(currency, holding.valuation().value()).getAmount();
                continue;
            }
            if (!holding.isInstrument())
            {
                unsupported.add(holding.type());
                continue;
            }
            var instrument = toInstrument(holding, Optional.ofNullable(byUuid.get(holding.uuid())));
            held.add(instrument);
            var reason = problem(holding, currency);
            if (reason.isPresent())
            {
                diagnostics.add(new Diagnostic(instrument, reason.get()));
                continue;
            }
            var value = Money.ofDecimal(currency, holding.valuation().value());
            if (value.getAmount() <= 0)
            {
                // worth less than one hundredth: a valuation gap, not a holding
                diagnostics.add(new Diagnostic(instrument, Reason.MISSING_VALUATION));
                continue;
            }
            positions.add(new Position(instrument, holding.shares().orElse(BigDecimal.ZERO), value));
            total += value.getAmount();
        }
        var valuation = new SecuritiesValuation(holdings.date(), positions, diagnostics, Money.of(currency, total));
        return new Scope(holdings.date(), currency, valuation, held, Money.of(currency, cash), unsupported);
    }

    private static Optional<Reason> problem(Holding holding, String reportingCurrency)
    {
        var valuation = holding.valuation().value();
        if (holding.shares().filter(shares -> shares.signum() < 0).isPresent() || valuation.signum() < 0)
            return Optional.of(Reason.UNSUPPORTED_NEGATIVE_POSITION);
        if (holding.price().isEmpty() || holding.price().get().value().signum() == 0 || valuation.signum() == 0)
            return Optional.of(Reason.MISSING_VALUATION);
        if (!holding.valuation().currency().equals(reportingCurrency))
            return Optional.of(Reason.MISSING_CONVERSION);
        var priceCurrency = holding.price().get().currency();
        if (!priceCurrency.equals(reportingCurrency))
        {
            var local = holding.localValuation();
            if (local.isEmpty() || local.get().value().compareTo(valuation) == 0)
                return Optional.of(Reason.MISSING_CONVERSION);
        }
        return Optional.empty();
    }

    private static HeldInstrument toInstrument(Holding holding, Optional<Instrument> master)
    {
        var identifiers = new LinkedHashMap<String, String>();
        master.flatMap(Instrument::isin).map(String::trim).filter(s -> !s.isEmpty())
                        .ifPresent(isin -> identifiers.put(ISIN, isin));
        master.flatMap(Instrument::wkn).ifPresent(wkn -> identifiers.put(WKN, wkn));
        master.flatMap(Instrument::tickerSymbol).ifPresent(ticker -> identifiers.put(TICKER, ticker));
        var name = master.map(Instrument::name).orElse(holding.name());
        var currencyCode = master.flatMap(Instrument::currencyCode).orElse(null);
        return new HeldInstrument(holding.uuid(), name, currencyCode, Map.copyOf(identifiers));
    }

    /** The identifiers a provider may receive for this instrument: the ISIN only. */
    public static Map<String, String> providerIdentifiers(HeldInstrument instrument)
    {
        var isin = instrument.identifiers().get(ISIN);
        return isin == null ? Map.of() : Map.of(ISIN, isin);
    }
}
