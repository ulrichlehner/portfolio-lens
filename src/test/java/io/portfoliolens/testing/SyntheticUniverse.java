package io.portfoliolens.testing;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.portfoliolens.http.HttpGet;
import io.portfoliolens.http.JdkHttpGet;

/**
 * A generated portfolio for size and budget tests: invented companies (codes {@code XS00000nnnnn}),
 * invented funds holding overlapping slices of them ({@code XF00000nnnnn}), a few directly held
 * companies, and an {@link HttpGet} that answers PP on loopback from the fake server and the
 * provider hosts from this universe. Every name, code and amount is invented.
 */
public final class SyntheticUniverse implements HttpGet
{
    private static final String[] COUNTRIES = { "United States", "Germany", "Japan", "France", "United Kingdom",
                    "Switzerland", "Canada", "Netherlands", "Australia", "Korea, Republic of" };
    private static final String[] SECTORS = { "Technology", "Healthcare", "Financial Services", "Industrials",
                    "Consumer Cyclical", "Energy", "Utilities", "Basic Materials", "Real Estate",
                    "Communication Services", "Consumer Defensive" };

    public record Fund(String isin, String name, List<Integer> companies)
    {
    }

    private final int companies;
    private final List<Fund> funds = new ArrayList<>();
    private final List<Integer> direct = new ArrayList<>();
    private final HttpGet loopback = new JdkHttpGet("PortfolioLens/test");
    private final AtomicInteger providerRequests = new AtomicInteger();
    private volatile int failEvery;
    private volatile String alwaysFailing = "";
    private volatile int outside;
    private final java.util.Set<String> awkward = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> malformedHoldings = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> requestsByPath = new LinkedHashMap<>();

    /**
     * @param companies
     *            number of distinct companies
     * @param fundCount
     *            number of funds; each holds an overlapping slice
     */
    public SyntheticUniverse(int companies, int fundCount, int directCount)
    {
        this.companies = companies;
        for (int f = 0; f < fundCount; f++)
        {
            var members = new ArrayList<Integer>();
            // slices of 40 % of the universe, shifted per fund, so funds overlap
            int size = Math.max(1, companies * 2 / 5);
            int start = f * companies / Math.max(1, fundCount);
            for (int i = 0; i < size; i++)
                members.add((start + i) % companies);
            funds.add(new Fund(fundIsin(f), "Invented Fund " + (f + 1), members));
        }
        for (int d = 0; d < directCount; d++)
            direct.add(d * 7 % companies);
    }

    public static String companyIsin(int index)
    {
        return String.format(Locale.ROOT, "XS00000%05d", index + 1);
    }

    public static String fundIsin(int index)
    {
        return String.format(Locale.ROOT, "XF00000%05d", index + 1);
    }

    /** Every fifth company has an all-caps name, as some providers deliver them. */
    public static String companyName(int index)
    {
        var name = "Invented Company " + (index + 1) + (index % 3 == 0 ? " Inc." : " AG");
        return index % 5 == 1 ? name.toUpperCase(Locale.ROOT) : name;
    }

    /** Every 15th company is unknown to the provider: no profile, not found by search. */
    public static boolean unknownToProvider(int index)
    {
        return index % 15 == 14;
    }

    /** Every third company reports revenue by region; the others have none. */
    public static boolean hasRevenue(int index)
    {
        return index % 3 == 0;
    }

    /**
     * Adds directly held companies that no fund holds, so their classification comes only from
     * the held instrument itself. Their indices follow the fund companies.
     */
    public void addDirectOutsideFunds(int count)
    {
        for (int i = 0; i < count; i++)
            direct.add(companies + outside + i);
        outside += count;
    }

    /** The fund composition request for this code answers with a body that is not JSON. */
    public void malformedHoldings(String isin)
    {
        malformedHoldings.add(isin);
    }

    /** The profile of this company has descriptive fields of unexpected types. */
    public void awkwardProfile(String isin)
    {
        awkward.add(isin);
    }

    public synchronized void resetCounts()
    {
        requestsByPath.clear();
        providerRequests.set(0);
    }

    public List<Fund> funds()
    {
        return funds;
    }

    /** Answers every request that names this code with HTTP 500. */
    public void alwaysFail(String code)
    {
        alwaysFailing = code;
    }

    /** Answers every n-th provider request with HTTP 500 (0 turns it off). */
    public void failEvery(int n)
    {
        failEvery = n;
    }

    public int providerRequests()
    {
        return providerRequests.get();
    }

    public synchronized Map<String, Integer> requestsByPath()
    {
        var result = new LinkedHashMap<String, Integer>();
        requestsByPath.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    /** The holdings report of the PP fake: every fund and the directly held companies. */
    public String holdingsJson()
    {
        var items = new ArrayList<String>();
        int uuid = 1;
        for (var fund : funds)
            items.add(holding(uuid++, fund.name(), 1000 + uuid * 10L));
        for (var company : direct)
            items.add(holding(uuid++, companyName(company), 200 + uuid));
        return "{\"date\":\"2026-07-20\",\"reportingCurrency\":\"EUR\",\"totalAssets\":{\"value\":1,\"currency\":\"EUR\"},"
                        + "\"items\":[" + String.join(",", items) + "]}";
    }

    public String instrumentsJson()
    {
        var items = new ArrayList<String>();
        int uuid = 1;
        for (var fund : funds)
            items.add(instrument(uuid++, fund.name(), fund.isin()));
        for (var company : direct)
            items.add(instrument(uuid++, companyName(company), companyIsin(company)));
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private static String uuid(int n)
    {
        return String.format(Locale.ROOT, "00000000-0000-4000-9000-%012d", n);
    }

    private static String holding(int n, String name, long value)
    {
        return "{\"type\":\"instrument\",\"uuid\":\"" + uuid(n) + "\",\"name\":\"" + name + "\",\"shares\":10,"
                        + "\"price\":{\"value\":" + value / 10.0 + ",\"currency\":\"EUR\",\"date\":\"2026-07-17\"},"
                        + "\"valuation\":{\"value\":" + value + ",\"currency\":\"EUR\"},\"weight\":0.01}";
    }

    private static String instrument(int n, String name, String isin)
    {
        return "{\"uuid\":\"" + uuid(n) + "\",\"name\":\"" + name + "\",\"currencyCode\":\"EUR\",\"isin\":\"" + isin
                        + "\"}";
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException
    {
        if ("127.0.0.1".equals(uri.getHost()))
            return loopback.get(uri, headers);
        var number = providerRequests.incrementAndGet();
        if (failEvery > 0 && number % failEvery == 0)
            return RecordingHttp.json(500, "");
        if (!alwaysFailing.isEmpty() && uri.toString().contains(alwaysFailing))
            return RecordingHttp.json(500, "");
        var path = uri.getPath().replaceAll("/[A-Z]{2}[0-9A-Z]{10}$", "/{isin}");
        synchronized (this)
        {
            requestsByPath.computeIfAbsent(uri.getHost() + path, ignored -> new AtomicInteger()).incrementAndGet();
        }
        if ("api.gleif.org".equals(uri.getHost()))
            return RecordingHttp.json(200, "{\"meta\":{\"pagination\":{\"total\":0}},\"data\":[]}");
        var p = uri.getPath();
        if (p.startsWith("/api/1/etf/holdings/"))
        {
            var isin = p.substring(p.lastIndexOf('/') + 1);
            if (malformedHoldings.contains(isin))
                return RecordingHttp.json(200, "{");
            var fund = funds.stream().filter(f -> f.isin().equals(isin)).findFirst();
            if (fund.isEmpty())
                return RecordingHttp.json(404, "");
            var rows = new ArrayList<String>();
            var members = fund.get().companies();
            // weights decline with the position, sum to 100 %
            double total = 0;
            for (int i = 0; i < members.size(); i++)
                total += 1.0 / (i + 1);
            for (int i = 0; i < members.size(); i++)
            {
                var c = members.get(i);
                rows.add("{\"etf_symbol\":\"" + isin + "\",\"symbol\":\"S" + c + "\",\"name\":\"" + companyName(c)
                                + "\",\"isin\":\"" + companyIsin(c) + "\",\"cusip\":null,\"percent\":"
                                + String.format(Locale.ROOT, "%.10f", 100.0 / (i + 1) / total)
                                + ",\"share\":1,\"value\":1,\"assetType\":null,\"logo\":null,"
                                + "\"updated_at\":\"2026-07-01T00:00:00Z\"}");
            }
            return RecordingHttp.json(200, "[" + String.join(",", rows) + "]");
        }
        if (p.startsWith("/api/1/equity/profile/"))
        {
            var isin = p.substring(p.lastIndexOf('/') + 1);
            if (!isin.startsWith("XS00000"))
                return RecordingHttp.json(404, "");
            var c = Integer.parseInt(isin.substring(7)) - 1;
            if (c < 0 || c >= companies + outside || unknownToProvider(c))
                return RecordingHttp.json(404, "");
            if (awkward.contains(isin))
                return RecordingHttp.json(200, "{\"isin\":\"" + isin + "\",\"name\":\"" + companyName(c)
                                + "\",\"country\":0,\"currency\":null,\"sector\":\"" + SECTORS[c % SECTORS.length]
                                + "\",\"industry\":[],\"mcap\":\"n/a\"}");
            return RecordingHttp.json(200, "{\"isin\":\"" + isin + "\",\"ticker\":\"S" + c + "\",\"name\":\""
                            + companyName(c) + "\",\"logo\":null,\"country\":\"" + COUNTRIES[c % COUNTRIES.length]
                            + "\",\"currency\":\"EUR\",\"employees\":1,\"sector\":\"" + SECTORS[c % SECTORS.length]
                            + "\",\"industry\":\"Invented industry " + (c % 40) + "\",\"ipo\":null,\"mcap\":1000.0,"
                            + "\"shares\":1.0,\"website\":null,\"wkn\":null,\"description\":\"Invented\"}");
        }
        if (p.equals("/api/1/equity/search"))
        {
            var query = uri.getQuery() == null ? "" : uri.getQuery();
            var fund = funds.stream().filter(f -> query.contains(f.isin())).findFirst();
            if (fund.isPresent())
                return RecordingHttp.json(200, "{\"count\":1,\"results\":[{\"isin\":\"" + fund.get().isin()
                                + "\",\"name\":\"Invented ETF\",\"ticker\":\"ETF\",\"type\":\"etf\",\"currency\":null}]}");
            return RecordingHttp.json(200, "{\"count\":0,\"results\":[]}");
        }
        if (p.startsWith("/api/1/bonds/list"))
            return RecordingHttp.json(200, "{\"data\":[],\"total\":0,\"offset\":0,\"limit\":50}");
        if (p.startsWith("/api/1/equity/regions/"))
        {
            var isin = p.substring(p.lastIndexOf('/') + 1);
            if (isin.startsWith("XS00000"))
            {
                var c = Integer.parseInt(isin.substring(7)) - 1;
                if (c >= 0 && c < companies + outside && !unknownToProvider(c) && hasRevenue(c))
                    return RecordingHttp.json(200, "[{\"period\":\"2025-12-31\",\"unit\":\"u_usd\","
                                    + "\"axis\":\"srt_StatementGeographicalAxis\",\"label\":\"United States\","
                                    + "\"value\":6,\"percentage\":60,\"member\":\"country_US\",\"symbol\":\"S\"},"
                                    + "{\"period\":\"2025-12-31\",\"unit\":\"u_usd\",\"axis\":\"srt_StatementGeographicalAxis\","
                                    + "\"label\":\"Europe\",\"value\":4,\"percentage\":40,\"member\":\"Europe\",\"symbol\":\"S\"}]");
            }
            return RecordingHttp.json(200, "[]");
        }
        return RecordingHttp.json(404, "");
    }
}
