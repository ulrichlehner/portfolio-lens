package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.portfoliolens.page.TreemapLayout.Rect;

public class TreemapLayoutTest
{
    private static final Rect AREA = new Rect(0, 0, 600, 400);

    @Test
    public void testTilesCoverTheAreaInProportion()
    {
        var weights = List.of(6d, 6d, 4d, 3d, 2d, 2d, 1d);
        var tiles = TreemapLayout.squarify(weights, w -> w, AREA);
        assertEquals(weights.size(), tiles.size());
        var area = tiles.stream().mapToDouble(t -> t.rect().area()).sum();
        assertEquals(AREA.area(), area, 1e-6);
        var total = weights.stream().mapToDouble(Double::doubleValue).sum();
        for (var tile : tiles)
            assertEquals(tile.item() / total * AREA.area(), tile.rect().area(), 1e-6);
        for (var tile : tiles)
        {
            var r = tile.rect();
            assertTrue(r.x() >= -1e-9 && r.y() >= -1e-9 && r.x() + r.width() <= 600 + 1e-6
                            && r.y() + r.height() <= 400 + 1e-6, r.toString());
        }
    }

    @Test
    public void testTilesAreSquarishForThePaperExample()
    {
        // the example of the paper: 6 x 4 area, weights 6 6 4 3 2 2 1
        var tiles = TreemapLayout.squarify(List.of(6d, 6d, 4d, 3d, 2d, 2d, 1d), w -> w, new Rect(0, 0, 6, 4));
        var worst = tiles.stream().mapToDouble(t -> Math.max(t.rect().width() / t.rect().height(),
                        t.rect().height() / t.rect().width())).max().orElseThrow();
        assertTrue(worst < 3, "worst aspect ratio " + worst);
    }

    @Test
    public void testHundredsOfTinyCompaniesKeepOwnTilesInSizeOrder()
    {
        var weights = new ArrayList<Double>();
        weights.add(1000d);
        IntStream.range(0, 500).forEach(i -> weights.add(0.01 + i * 0.0001));
        var tiles = TreemapLayout.squarify(weights, w -> w, AREA);
        assertEquals(501, tiles.size());
        assertEquals(1000d, tiles.get(0).item());
        for (int i = 1; i < tiles.size(); i++)
            assertTrue(tiles.get(i - 1).item() >= tiles.get(i).item());
        assertTrue(tiles.stream().allMatch(t -> t.rect().area() > 0));
        assertEquals(AREA.area(), tiles.stream().mapToDouble(t -> t.rect().area()).sum(), 1e-6);
    }

    @Test
    public void testZeroWeightsAndEmptyAreaAreSkipped()
    {
        assertTrue(TreemapLayout.squarify(List.of(0d, -1d), w -> w, AREA).isEmpty());
        assertTrue(TreemapLayout.squarify(List.of(1d), w -> w, new Rect(0, 0, 0, 10)).isEmpty());
    }

    @Test
    public void testGroupsGetHeaderStripAndMembersInside()
    {
        record Group(String name, List<Double> members)
        {
            double total()
            {
                return members.stream().mapToDouble(Double::doubleValue).sum();
            }
        }
        var groups = List.of(new Group("A", List.of(5d, 3d)), new Group("B", List.of(2d)));
        var layout = TreemapLayout.grouped(groups, Group::total, Group::members, w -> w, AREA);
        assertEquals(2, layout.size());
        var first = layout.get(0);
        assertTrue(first.header());
        for (var member : first.members())
        {
            assertTrue(member.rect().y() >= first.rect().y() + TreemapLayout.GROUP_HEADER - 1e-9);
            assertTrue(member.rect().x() >= first.rect().x());
        }
        var memberArea = first.members().stream().mapToDouble(t -> t.rect().area()).sum();
        var inner = (first.rect().width() - 2 * TreemapLayout.GROUP_BORDER)
                        * (first.rect().height() - TreemapLayout.GROUP_HEADER - TreemapLayout.GROUP_BORDER);
        assertEquals(inner, memberArea, 1e-6);
    }

    @Test
    public void testMergedTileIsPlacedLast()
    {
        var tiles = TreemapLayout.squarify(List.of(50d, 10d, 8d, 6d), w -> w, w -> w == 50d, AREA);
        assertEquals(50d, tiles.get(tiles.size() - 1).item());
        assertEquals(10d, tiles.get(0).item());
        assertEquals(AREA.area(), tiles.stream().mapToDouble(t -> t.rect().area()).sum(), 1e-6);
    }
}
