package io.portfoliolens.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in hundredths of a currency unit, the unit the ported calculation works in. Amounts
 * from the PP REST API are decimals and are converted with {@link #ofDecimal}.
 */
public record Money(String currencyCode, long amount)
{
    /** Hundredths per currency unit. */
    public static final int FACTOR = 100;

    public Money
    {
        Objects.requireNonNull(currencyCode);
    }

    public static Money of(String currencyCode, long amount)
    {
        return new Money(currencyCode, amount);
    }

    /** Rounds a decimal amount half-even to hundredths. */
    public static Money ofDecimal(String currencyCode, BigDecimal value)
    {
        return new Money(currencyCode, value.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValueExact());
    }

    public long getAmount()
    {
        return amount;
    }

    public String getCurrencyCode()
    {
        return currencyCode;
    }

    public BigDecimal toDecimal()
    {
        return BigDecimal.valueOf(amount, 2);
    }
}
