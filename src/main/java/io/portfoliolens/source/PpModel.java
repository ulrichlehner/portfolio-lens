package io.portfoliolens.source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The parts of PP REST v1 responses that Lens reads. Unknown fields are ignored when parsing. */
public final class PpModel
{
    private PpModel()
    {
    }

    public record ApiVersion(String apiVersion, Optional<String> applicationVersion)
    {
        /** The major version number, or -1 when it cannot be read. */
        public int major()
        {
            try
            {
                return Integer.parseInt(apiVersion.split("\\.", 2)[0]);
            }
            catch (NumberFormatException e)
            {
                return -1;
            }
        }
    }

    /** An enabled file. The path is never shown on the page or logged. */
    public record PpFile(String id, Optional<String> alias, String label, String path)
    {
        @Override
        public String toString()
        {
            return "PpFile[id=" + id + ", alias=" + alias.orElse("") + "]";
        }
    }

    public record MonetaryValue(BigDecimal value, String currency)
    {
    }

    public record Price(BigDecimal value, String currency, Optional<LocalDate> date)
    {
    }

    /** Holding types Lens knows; the set is open in PP, other values stay as text. */
    public static final String INSTRUMENT = "instrument";
    public static final String CASH_ACCOUNT = "cash-account";

    public record Holding(String type, String uuid, String name, Optional<BigDecimal> shares, Optional<Price> price,
                    MonetaryValue valuation, BigDecimal weight, Optional<MonetaryValue> localValuation)
    {
        public boolean isInstrument()
        {
            return INSTRUMENT.equals(type);
        }

        public boolean isCashAccount()
        {
            return CASH_ACCOUNT.equals(type);
        }
    }

    public record Holdings(LocalDate date, String reportingCurrency, MonetaryValue totalAssets, List<Holding> items)
    {
        public Holdings
        {
            Objects.requireNonNull(date);
            items = List.copyOf(items);
        }
    }

    public record Instrument(String uuid, String name, Optional<String> currencyCode, Optional<String> isin,
                    Optional<String> wkn, Optional<String> tickerSymbol)
    {
    }
}
