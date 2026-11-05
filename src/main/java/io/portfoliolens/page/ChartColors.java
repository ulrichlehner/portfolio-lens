package io.portfoliolens.page;

import java.util.Locale;

/**
 * Colors of the company charts, ported from the user's fork: green for value held directly, blue
 * for value held through funds, and a treemap tile color that blends between them by the direct
 * part of a company. Both are mid-tones that stay readable on light and dark backgrounds.
 */
public final class ChartColors
{
    /** Held directly: #2CA02C. */
    public static final int[] DIRECT = { 44, 160, 44 };
    /** Held through funds: #1F77B4. */
    public static final int[] INDIRECT = { 31, 119, 180 };

    private ChartColors()
    {
    }

    public static String hex(int[] rgb)
    {
        return String.format(Locale.ROOT, "#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }

    /** Blends from the fund color to the direct color by the direct part, in 20 steps. */
    public static String tileColor(double directFraction)
    {
        var fraction = Math.round(Math.max(0, Math.min(1, directFraction)) * 20) / 20d;
        return hex(new int[] { blend(INDIRECT[0], DIRECT[0], fraction), blend(INDIRECT[1], DIRECT[1], fraction),
                        blend(INDIRECT[2], DIRECT[2], fraction) });
    }

    private static int blend(int from, int to, double fraction)
    {
        return (int) Math.round(from + (to - from) * fraction);
    }
}
