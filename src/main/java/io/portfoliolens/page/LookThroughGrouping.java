package io.portfoliolens.page;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.portfoliolens.core.CompanyAttributes;
import io.portfoliolens.core.CompanyAttributes.Attribute;
import io.portfoliolens.core.RevenueSplit;

/**
 * The ways to group the companies of the Breakdown and the classifications
 * behind them: Lens's own region table, the GICS sectors and English
 * country names (see {@link Classifications}). The class is SWT-free.
 */
public final class LookThroughGrouping
{
    /** Attribute that the Breakdown groups its companies by. */
    public enum Grouping
    {
        COMPANY, COUNTRY, REGION, SECTOR, INDUSTRY, CURRENCY, REVENUE_REGION;

        public String label()
        {
            return switch (this)
            {
                case COMPANY -> Texts.LookThroughGroupingCompany;
                case COUNTRY -> Texts.LookThroughGroupingCountry;
                case REGION -> Texts.LookThroughGroupingRegion;
                case SECTOR -> Texts.LookThroughGroupingSector;
                case INDUSTRY -> Texts.LookThroughGroupingIndustry;
                case CURRENCY -> Texts.LookThroughGroupingCurrency;
                case REVENUE_REGION -> Texts.LookThroughGroupingRevenueRegion;
            };
        }
    }

    /** One group of a grouping: a stable code for the row key and the label to display. */
    public record Group(String code, String label)
    {
    }

    /** Provider sector names that differ from the GICS names, in lower case. */
    private static final Map<String, String> SECTOR_ALIASES = Map.of( //
                    "technology", "45",
                    "healthcare", "35",
                    "financial services", "40",
                    "financials", "40",
                    "consumer cyclical", "25",
                    "consumer defensive", "30",
                    "basic materials", "15");

    private static final Map<String, Classifications.Sector> SECTORS_BY_NAME = Classifications.sectorsByName();

    /** Whether the grouping needs the company attributes. */
    public static boolean usesAttributes(Grouping grouping)
    {
        return grouping != Grouping.COMPANY;
    }

    /**
     * The group of a company with the given attributes, or empty when the
     * attribute is unknown or has no entry in the classification.
     */
    public Optional<Group> groupOf(Grouping grouping, CompanyAttributes attributes)
    {
        return switch (grouping)
        {
            case COMPANY -> Optional.empty();
            case COUNTRY -> value(attributes.country()).map(this::country);
            case REGION -> value(attributes.country()).flatMap(Classifications::region)
                            .map(region -> new Group(region.code(), region.name()));
            case SECTOR -> value(attributes.sector()).map(this::sector);
            case INDUSTRY -> value(attributes.industry()).map(name -> new Group(name.toLowerCase(Locale.ROOT), name));
            case CURRENCY -> value(attributes.currency()).map(code -> new Group(code, code));
            // split over several groups, see revenueGroup
            case REVENUE_REGION -> Optional.empty();
        };
    }

    /** The country with its English name. */
    public Group country(String code)
    {
        return new Group(code, Classifications.countryName(code));
    }

    /**
     * The group of a revenue member: a country, a region, one of the
     * revenue-only regions, or the remainder.
     */
    public Group revenueGroup(RevenueSplit.Member member)
    {
        var code = member.code();
        return switch (member.kind())
        {
            case COUNTRY -> country(code);
            case REGION -> new Group(member.key(), switch (code)
            {
                case RevenueSplit.EMEA -> Texts.LookThroughRegionEmea;
                case RevenueSplit.APAC -> Texts.LookThroughRegionApac;
                case RevenueSplit.LATAM -> Texts.LookThroughRegionLatam;
                case RevenueSplit.GREATER_CHINA -> Texts.LookThroughRegionGreaterChina;
                default -> Classifications.revenueRegionName(code).orElse(code);
            });
            case OTHER -> new Group(member.key(), Texts.LookThroughRegionOther);
        };
    }

    /**
     * The GICS sector when the provider name equals its
     * English name (or a known alias); otherwise the name as delivered.
     */
    public Group sector(String name)
    {
        var normalized = name.trim().toLowerCase(Locale.ROOT);
        var sector = SECTORS_BY_NAME.get(normalized);
        if (sector == null)
        {
            var code = SECTOR_ALIASES.get(normalized);
            if (code != null)
                sector = Classifications.SECTORS.stream().filter(s -> s.code().equals(code)).findFirst().orElse(null);
        }
        return sector != null ? new Group(sector.code(), sector.name()) : new Group("name:" + normalized, name.trim());
    }

    private static <T> Optional<T> value(Optional<Attribute<T>> attribute)
    {
        return attribute.map(Attribute::value);
    }
}
