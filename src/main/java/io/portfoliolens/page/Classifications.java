package io.portfoliolens.page;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Classification tables written for Portfolio Lens: seven regions by ISO 3166 alpha-2 country
 * code, the names of the larger regions that revenue reports use, and the 11 GICS sectors. Country
 * names come from {@link Locale} in English. These tables are facts, compiled for this project;
 * they are not copied from Portfolio Performance.
 */
public final class Classifications
{
    public record Region(String code, String name)
    {
    }

    public record Sector(String code, String name)
    {
    }

    public static final Region AFRICA = new Region("africa", "Africa");
    public static final Region ASIA = new Region("asia", "Asia");
    public static final Region EUROPE = new Region("europe", "Europe");
    public static final Region LATIN_AMERICA = new Region("latin-america", "Latin America and Caribbean");
    public static final Region MIDDLE_EAST = new Region("middle-east", "Middle East");
    public static final Region NORTH_AMERICA = new Region("north-america", "North America");
    public static final Region OCEANIA = new Region("oceania", "Oceania");

    private static final Map<String, Region> REGIONS = new HashMap<>();

    private static void add(Region region, String codes)
    {
        for (var code : codes.trim().split("\\s+"))
            if (REGIONS.put(code, region) != null)
                throw new IllegalStateException("country listed twice: " + code);
    }

    static
    {
        add(AFRICA, """
                        DZ AO BJ BW BF BI CV CM CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW
                        ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW""");
        add(ASIA, """
                        AF BD BT BN KH CN HK IN ID JP KZ KG LA MO MY MV MN MM NP KP PK PH SG KR LK TW TJ TH TL TM UZ
                        VN""");
        add(EUROPE, """
                        AX AL AD AM AT AZ BY BE BA BG HR CY CZ DK EE FO FI FR GE DE GI GR GG HU IS IE IM IT JE XK LV
                        LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH TR UA GB VA""");
        add(MIDDLE_EAST, "AE BH IL IQ IR JO KW LB OM PS QA SA SY YE");
        add(NORTH_AMERICA, "US CA BM GL PM");
        add(LATIN_AMERICA, """
                        AI AG AR AW BS BB BZ BO BQ BR VG KY CL CO CR CU CW DM DO EC SV FK GF GD GP GT GY HT HN JM MQ
                        MX MS NI PA PY PE PR BL KN LC MF VC SX SR TT TC UY VE VI""");
        add(OCEANIA, "AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV UM VU WF");
    }

    /**
     * Names of the region codes that revenue reports map to (see {@code RevenueSplit}); the codes
     * are opaque keys of the calculation.
     */
    private static final Map<String, String> REVENUE_REGIONS = Map.of( //
                    "R10", "Europe", //
                    "R20", "Americas", //
                    "R2010", "North America", //
                    "R2040", "South America", //
                    "R30", "Asia", //
                    "R40", "Africa", //
                    "R50", "Oceania");

    /** The 11 GICS sectors, by code. */
    public static final List<Sector> SECTORS = List.of( //
                    new Sector("10", "Energy"), //
                    new Sector("15", "Materials"), //
                    new Sector("20", "Industrials"), //
                    new Sector("25", "Consumer Discretionary"), //
                    new Sector("30", "Consumer Staples"), //
                    new Sector("35", "Health Care"), //
                    new Sector("40", "Financials"), //
                    new Sector("45", "Information Technology"), //
                    new Sector("50", "Communication Services"), //
                    new Sector("55", "Utilities"), //
                    new Sector("60", "Real Estate"));

    private Classifications()
    {
    }

    /** The region of a country, or empty when the code is not in the table. */
    public static Optional<Region> region(String countryCode)
    {
        return Optional.ofNullable(REGIONS.get(countryCode.toUpperCase(Locale.ROOT)));
    }

    /** The English name of a country, or the code when the JDK does not know it. */
    public static String countryName(String countryCode)
    {
        var name = Locale.of("", countryCode.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
        return name.isBlank() || name.equalsIgnoreCase(countryCode) ? countryCode : name;
    }

    /** The name of a revenue region code, if known. */
    public static Optional<String> revenueRegionName(String code)
    {
        return Optional.ofNullable(REVENUE_REGIONS.get(code));
    }

    /** Sectors by lower-case English name. */
    public static Map<String, Sector> sectorsByName()
    {
        var result = new LinkedHashMap<String, Sector>();
        SECTORS.forEach(sector -> result.put(sector.name().toLowerCase(Locale.ROOT), sector));
        return result;
    }
}
