package io.portfoliolens.page;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.portfoliolens.core.FundOverlap;
import io.portfoliolens.core.FundOverlap.Pair;

/**
 * What the overlap matrix shows, without any drawing: the fund names, and per
 * pair the text, the shade and the tooltip. The matrix has the same fund order
 * on both axes; the chart draws every pair only once.
 */
final class LookThroughOverlapModel
{
    /** Number of shared companies listed in a tooltip. */
    static final int TOOLTIP_COMPANIES = 5;

    /**
     * One off-diagonal cell. The shade is the overlap relative to the largest
     * overlap of the matrix, so small overlaps stay distinguishable; the text
     * always shows the absolute overlap and the detail the number of shared
     * companies.
     */
    record Cell(String text, String detail, double shade, String tooltip)
    {
    }

    private static final LookThroughOverlapModel EMPTY = new LookThroughOverlapModel(List.of(), List.of(), List.of());

    private final List<String> names;
    private final List<String> excluded;
    private final List<List<Cell>> cells;

    private LookThroughOverlapModel(List<String> names, List<String> excluded, List<List<Cell>> cells)
    {
        this.names = names;
        this.excluded = excluded;
        this.cells = cells;
    }

    static LookThroughOverlapModel empty()
    {
        return EMPTY;
    }

    static LookThroughOverlapModel of(FundOverlap overlap)
    {
        if (overlap == null)
            return EMPTY;

        var funds = overlap.funds();
        var excluded = overlap.excluded().stream().map(security -> security.name()).toList();
        if (funds.size() < 2)
            return new LookThroughOverlapModel(List.of(), excluded, List.of());

        var maximum = BigDecimal.ZERO;
        for (int ii = 0; ii < funds.size(); ii++)
            for (int jj = ii + 1; jj < funds.size(); jj++)
                maximum = maximum.max(overlap.pair(ii, jj).overlap());

        var names = funds.stream().map(FundOverlap.Fund::name).toList();
        var cells = new ArrayList<List<Cell>>();
        for (int ii = 0; ii < funds.size(); ii++)
        {
            var row = new ArrayList<Cell>();
            for (int jj = 0; jj < funds.size(); jj++)
                row.add(ii == jj ? null : cell(overlap.pair(ii, jj), maximum));
            cells.add(row);
        }
        return new LookThroughOverlapModel(names, excluded, cells);
    }

    private static Cell cell(Pair pair, BigDecimal maximum)
    {
        var shade = maximum.signum() > 0 ? pair.overlap().divide(maximum, MathContext.DECIMAL64).doubleValue()
                        : 0d;
        var text = percent(pair.overlap());

        var lines = new ArrayList<String>();
        lines.add(pair.first().name() + " / " + pair.second().name());
        lines.add(Texts.LookThroughChartOverlap + ": " + text);
        lines.add(MessageFormat.format(Texts.LookThroughOverlapShared, pair.sharedCount()));
        if (!pair.shared().isEmpty())
        {
            lines.add(Texts.LookThroughOverlapLargestShared + ":");
            pair.shared().stream().limit(TOOLTIP_COMPANIES)
                            .forEach(shared -> lines.add(shared.label() + ": " + percent(shared.overlap())));
        }
        return new Cell(text, MessageFormat.format(Texts.LookThroughOverlapSharedCount, pair.sharedCount()),
                        shade, String.join("\n", lines));
    }

    private static String percent(BigDecimal fraction)
    {
        return Values.Percent2.format(fraction.doubleValue());
    }

    /** At least two decomposed funds are needed for a matrix. */
    boolean isEmpty()
    {
        return names.size() < 2;
    }

    int size()
    {
        return names.size();
    }

    String name(int index)
    {
        return names.get(index);
    }

    /** The cell of two different funds; the diagonal has none. */
    Cell cell(int row, int column)
    {
        return cells.get(row).get(column);
    }

    /** The funds that are held but not decomposed, as a text; empty if there are none. */
    Optional<String> excludedText()
    {
        if (excluded.isEmpty())
            return Optional.empty();
        return Optional.of(MessageFormat.format(Texts.LookThroughOverlapExcluded,
                        excluded.stream().collect(Collectors.joining(", "))));
    }
}
