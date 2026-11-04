package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

public class ClassificationsTest
{
    /** Territories without a region on purpose: Antarctica and uninhabited territories. */
    private static final Set<String> WITHOUT_REGION = Set.of("AQ", "BV", "HM", "IO", "TF", "GS", "CX", "CC");

    @Test
    public void testEveryInhabitedIsoCountryHasARegion()
    {
        var missing = new TreeSet<String>();
        for (var code : Locale.getISOCountries())
            if (Classifications.region(code).isEmpty() && !WITHOUT_REGION.contains(code))
                missing.add(code);
        assertEquals(Set.of(), missing);
    }

    @Test
    public void testExamples()
    {
        assertEquals(Classifications.NORTH_AMERICA, Classifications.region("US").orElseThrow());
        assertEquals(Classifications.EUROPE, Classifications.region("de").orElseThrow());
        assertEquals(Classifications.LATIN_AMERICA, Classifications.region("BR").orElseThrow());
        assertEquals(Classifications.MIDDLE_EAST, Classifications.region("AE").orElseThrow());
        assertEquals(Optional.empty(), Classifications.region("ZZ"));
        assertEquals("Germany", Classifications.countryName("DE"));
        assertEquals("ZZ", Classifications.countryName("ZZ"));
        assertEquals(Optional.of("Americas"), Classifications.revenueRegionName("R20"));
    }

    @Test
    public void testElevenSectorsAndProviderAlias()
    {
        assertEquals(11, Classifications.SECTORS.size());
        var grouping = new LookThroughGrouping();
        assertEquals(new LookThroughGrouping.Group("45", "Information Technology"), grouping.sector("Technology"));
        assertEquals(new LookThroughGrouping.Group("35", "Health Care"), grouping.sector("health care"));
        assertTrue(grouping.sector("Invented sector").code().startsWith("name:"));
    }
}
