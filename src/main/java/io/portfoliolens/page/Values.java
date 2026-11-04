package io.portfoliolens.page;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.portfoliolens.core.Money;

/** Text formats of the page: amounts, percentages and dates, always in English notation. */
public final class Values
{
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ENGLISH);

    /** A percentage of a fraction (0.5 is 50.00%). */
    public static final class PercentFormat
    {
        private final String pattern;

        private PercentFormat(String pattern)
        {
            this.pattern = pattern;
        }

        public String format(double fraction)
        {
            return new DecimalFormat(pattern, SYMBOLS).format(fraction);
        }
    }

    public static final class DateFormat
    {
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

        public String format(LocalDate date)
        {
            return FORMAT.format(date);
        }
    }

    public static final class DateTimeFormat
    {
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

        public String format(LocalDateTime dateTime)
        {
            return FORMAT.format(dateTime);
        }
    }

    /** An amount with its currency code, for example {@code EUR 1,234.56}. */
    public static final class MoneyFormat
    {
        public String format(Money money)
        {
            return money.getCurrencyCode() + " " + new DecimalFormat("#,##0.00", SYMBOLS).format(money.toDecimal());
        }
    }

    public static final PercentFormat Percent = new PercentFormat("0.00%");
    public static final PercentFormat Percent2 = new PercentFormat("0.00%");
    public static final DateFormat Date = new DateFormat();
    public static final DateTimeFormat DateTime = new DateTimeFormat();
    public static final MoneyFormat Money = new MoneyFormat();

    private Values()
    {
    }
}
