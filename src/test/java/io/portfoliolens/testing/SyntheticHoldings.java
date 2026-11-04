package io.portfoliolens.testing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.portfoliolens.core.HeldInstrument;
import io.portfoliolens.core.SecuritiesValuation;
import io.portfoliolens.source.PpModel.Holding;
import io.portfoliolens.source.PpModel.Holdings;
import io.portfoliolens.source.PpModel.Instrument;
import io.portfoliolens.source.PpModel.MonetaryValue;
import io.portfoliolens.source.PpModel.Price;
import io.portfoliolens.source.RestValuationAdapter;

/**
 * Builds a synthetic PP holdings report and values it with {@link RestValuationAdapter}, the
 * replacement for the PP client fixtures of the fork's tests. All instruments are in EUR.
 */
public final class SyntheticHoldings
{
    private record Line(HeldInstrument instrument, BigDecimal shares, Optional<BigDecimal> price)
    {
    }

    private final Map<HeldInstrument, Line> lines = new LinkedHashMap<>();
    private final List<Holding> cash = new ArrayList<>();

    /** Holds {@code shares} at {@code price} with the ISIN {@code isin}. */
    public HeldInstrument hold(String name, String isin, double shares, double price)
    {
        var instrument = new HeldInstrument(UUID.randomUUID().toString(), name, "EUR",
                        isin == null ? Map.of() : Map.of(RestValuationAdapter.ISIN, isin));
        lines.put(instrument, new Line(instrument, BigDecimal.valueOf(shares), Optional.of(BigDecimal.valueOf(price))));
        return instrument;
    }

    /** Holds shares without any price, so the holding has no valuation. */
    public HeldInstrument holdWithoutPrice(String name, String isin, double shares)
    {
        var instrument = hold(name, isin, shares, 0);
        lines.put(instrument, new Line(instrument, BigDecimal.valueOf(shares), Optional.empty()));
        return instrument;
    }

    public void setPrice(HeldInstrument instrument, double price)
    {
        var line = lines.get(instrument);
        lines.put(instrument, new Line(instrument, line.shares(), Optional.of(BigDecimal.valueOf(price))));
    }

    public void deposit(double amount)
    {
        cash.add(new Holding("cash-account", UUID.randomUUID().toString(), "Synthetic cash", Optional.empty(),
                        Optional.empty(), new MonetaryValue(BigDecimal.valueOf(amount), "EUR"), BigDecimal.ZERO,
                        Optional.empty()));
    }

    /** The report at the date for the given instruments (all held instruments when empty). */
    public Holdings report(LocalDate date, Collection<HeldInstrument> selected)
    {
        var items = new ArrayList<Holding>();
        for (var line : lines.values())
        {
            if (!selected.isEmpty() && !selected.contains(line.instrument()))
                continue;
            var value = line.price().map(price -> price.multiply(line.shares())).orElse(BigDecimal.ZERO);
            items.add(new Holding("instrument", line.instrument().uuid(), line.instrument().name(),
                            Optional.of(line.shares()),
                            line.price().filter(p -> p.signum() != 0)
                                            .map(p -> new Price(p, "EUR", Optional.of(date))),
                            new MonetaryValue(value, "EUR"), BigDecimal.ZERO, Optional.empty()));
        }
        items.addAll(cash);
        return new Holdings(date, "EUR", new MonetaryValue(BigDecimal.ZERO, "EUR"), items);
    }

    public List<Instrument> instruments()
    {
        return lines.keySet().stream()
                        .map(i -> new Instrument(i.uuid(), i.name(), Optional.of("EUR"),
                                        Optional.ofNullable(i.identifiers().get(RestValuationAdapter.ISIN)),
                                        Optional.empty(), Optional.empty()))
                        .toList();
    }

    public SecuritiesValuation valuation(LocalDate date, Collection<HeldInstrument> selected)
    {
        return new RestValuationAdapter().value(report(date, selected), instruments()).valuation();
    }
}
