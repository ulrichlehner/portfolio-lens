package io.portfoliolens.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Squarified treemap layout after Bruls, Huizing and van Wijk, "Squarified Treemaps" (2000),
 * written for Portfolio Lens. Items are placed in descending weight; each item keeps its own
 * tile however small, and the tiles of one level cover the given area completely.
 */
public final class TreemapLayout
{
    public record Rect(double x, double y, double width, double height)
    {
        public double area()
        {
            return width * height;
        }

        Rect inset(double left, double top, double right, double bottom)
        {
            return new Rect(x + left, y + top, Math.max(0, width - left - right), Math.max(0, height - top - bottom));
        }
    }

    public record Tile<T>(T item, Rect rect)
    {
    }

    /** A group tile with its header strip and the tiles of its members. */
    public record GroupTile<G, T>(G group, Rect rect, boolean header, List<Tile<T>> members)
    {
    }

    /** Height of the label strip of a group. */
    public static final double GROUP_HEADER = 16;
    /** Border inside a group around its members. */
    public static final double GROUP_BORDER = 2;
    /** Groups smaller than this get no header strip and no border. */
    public static final double MINIMUM_GROUP_WIDTH = 60;
    public static final double MINIMUM_GROUP_HEIGHT = 36;

    private TreemapLayout()
    {
    }

    /** Lays out the items with a positive weight in descending weight over the area. */
    public static <T> List<Tile<T>> squarify(List<T> items, ToDoubleFunction<T> weight, Rect area)
    {
        return squarify(items, weight, item -> false, area);
    }

    /**
     * Like {@link #squarify(List, ToDoubleFunction, Rect)}, but the items matching {@code last}
     * are placed after all others whatever their weight, for example a tile that merges the
     * smallest items.
     */
    public static <T> List<Tile<T>> squarify(List<T> items, ToDoubleFunction<T> weight,
                    java.util.function.Predicate<T> last, Rect area)
    {
        var byWeight = Comparator.comparingDouble(weight).reversed();
        var sorted = items.stream().filter(item -> weight.applyAsDouble(item) > 0)
                        .sorted(Comparator.comparing((T item) -> last.test(item)).thenComparing(byWeight)).toList();
        var result = new ArrayList<Tile<T>>();
        if (sorted.isEmpty() || area.area() <= 0)
            return result;
        var total = sorted.stream().mapToDouble(weight).sum();
        var scale = area.area() / total;

        var remaining = area;
        var row = new ArrayList<T>();
        double rowArea = 0;
        int index = 0;
        while (index < sorted.size())
        {
            var item = sorted.get(index);
            var itemArea = weight.applyAsDouble(item) * scale;
            var side = Math.min(remaining.width(), remaining.height());
            if (row.isEmpty() || worst(row, rowArea, weight, scale, side) >= worstWith(row, rowArea, itemArea, weight,
                            scale, side))
            {
                row.add(item);
                rowArea += itemArea;
                index++;
            }
            else
            {
                remaining = place(row, rowArea, weight, scale, remaining, result, false);
                row.clear();
                rowArea = 0;
            }
        }
        if (!row.isEmpty())
            place(row, rowArea, weight, scale, remaining, result, true);
        return result;
    }

    /**
     * Two levels: groups first, then the members of each group inside it below a header strip.
     * The group rectangle is always covered by the group itself, so no area stays unpainted even
     * where member tiles are too small to see.
     */
    public static <G, T> List<GroupTile<G, T>> grouped(List<G> groups, ToDoubleFunction<G> weight,
                    java.util.function.Function<G, List<T>> members, ToDoubleFunction<T> memberWeight, Rect area)
    {
        return grouped(groups, weight, members, memberWeight, item -> false, area);
    }

    public static <G, T> List<GroupTile<G, T>> grouped(List<G> groups, ToDoubleFunction<G> weight,
                    java.util.function.Function<G, List<T>> members, ToDoubleFunction<T> memberWeight,
                    java.util.function.Predicate<T> last, Rect area)
    {
        var result = new ArrayList<GroupTile<G, T>>();
        for (var tile : squarify(groups, weight, area))
        {
            var rect = tile.rect();
            var header = rect.width() >= MINIMUM_GROUP_WIDTH && rect.height() >= MINIMUM_GROUP_HEIGHT;
            var inner = header ? rect.inset(GROUP_BORDER, GROUP_HEADER, GROUP_BORDER, GROUP_BORDER) : rect;
            result.add(new GroupTile<>(tile.item(), rect, header,
                            squarify(members.apply(tile.item()), memberWeight, last, inner)));
        }
        return result;
    }

    private static <T> double worst(List<T> row, double rowArea, ToDoubleFunction<T> weight, double scale, double side)
    {
        double max = 0;
        double min = Double.MAX_VALUE;
        for (var item : row)
        {
            var value = weight.applyAsDouble(item) * scale;
            max = Math.max(max, value);
            min = Math.min(min, value);
        }
        return ratio(rowArea, max, min, side);
    }

    private static <T> double worstWith(List<T> row, double rowArea, double added, ToDoubleFunction<T> weight,
                    double scale, double side)
    {
        double max = added;
        double min = added;
        for (var item : row)
        {
            var value = weight.applyAsDouble(item) * scale;
            max = Math.max(max, value);
            min = Math.min(min, value);
        }
        return ratio(rowArea + added, max, min, side);
    }

    /** The worst aspect ratio of a row with the given total, largest and smallest area. */
    private static double ratio(double sum, double max, double min, double side)
    {
        if (sum <= 0 || min <= 0 || side <= 0)
            return Double.MAX_VALUE;
        var sideSquared = side * side;
        var sumSquared = sum * sum;
        return Math.max(sideSquared * max / sumSquared, sumSquared / (sideSquared * min));
    }

    /**
     * Places a row along the shorter side of the remaining area and returns what is left. The
     * last row takes the full remaining area so rounding leaves no gap.
     */
    private static <T> Rect place(List<T> row, double rowArea, ToDoubleFunction<T> weight, double scale, Rect area,
                    List<Tile<T>> result, boolean last)
    {
        var horizontal = area.width() >= area.height(); // row stands along the left edge
        if (horizontal)
        {
            var width = last ? area.width() : Math.min(area.width(), rowArea / area.height());
            double y = area.y();
            for (int i = 0; i < row.size(); i++)
            {
                var item = row.get(i);
                var height = i == row.size() - 1 ? area.y() + area.height() - y
                                : weight.applyAsDouble(item) * scale / width;
                result.add(new Tile<>(item, new Rect(area.x(), y, width, height)));
                y += height;
            }
            return new Rect(area.x() + width, area.y(), area.width() - width, area.height());
        }
        var height = last ? area.height() : Math.min(area.height(), rowArea / area.width());
        double x = area.x();
        for (int i = 0; i < row.size(); i++)
        {
            var item = row.get(i);
            var width = i == row.size() - 1 ? area.x() + area.width() - x : weight.applyAsDouble(item) * scale / height;
            result.add(new Tile<>(item, new Rect(x, area.y(), width, height)));
            x += width;
        }
        return new Rect(area.x(), area.y() + height, area.width(), area.height() - height);
    }
}
