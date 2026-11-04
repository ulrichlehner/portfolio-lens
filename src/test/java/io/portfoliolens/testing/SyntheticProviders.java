package io.portfoliolens.testing;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.portfoliolens.http.HttpGet;
import io.portfoliolens.http.JdkHttpGet;

/**
 * One {@link HttpGet} for a whole run in tests: loopback requests go to the fake PP server, the
 * Eulerpool and GLEIF hosts are answered with invented data. Provider requests are recorded.
 * Two synthetic ETFs hold public large caps with invented weights.
 */
public final class SyntheticProviders implements HttpGet
{
    public static final String WORLD_ETF = "IE00B4L5Y983";
    public static final String ALL_WORLD_ETF = "IE00BK5BQT80";
    public static final String APPLE = "US0378331005";
    public static final String MICROSOFT = "US5949181045";
    public static final String NVIDIA = "US67066G1040";
    public static final String SAP = "DE0007164600";

    private final HttpGet loopback = new JdkHttpGet("PortfolioLens/test");
    private final List<RecordingHttp.Request> requests = new ArrayList<>();
    private final AtomicLong delayMillis = new AtomicLong();

    /** Makes every provider answer take this long, to exercise the run time limit. */
    public void delay(long millis)
    {
        delayMillis.set(millis);
    }

    public synchronized List<RecordingHttp.Request> requests()
    {
        return List.copyOf(requests);
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException
    {
        if ("127.0.0.1".equals(uri.getHost()))
            return loopback.get(uri, headers);
        synchronized (this)
        {
            requests.add(new RecordingHttp.Request(uri, Map.copyOf(headers)));
        }
        try
        {
            if (delayMillis.get() > 0)
                Thread.sleep(delayMillis.get());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException();
        }
        if ("api.gleif.org".equals(uri.getHost()))
            return json(200, "{\"meta\":{\"pagination\":{\"total\":0}},\"data\":[]}");
        if (!"api.eulerpool.com".equals(uri.getHost()))
            return json(404, "");
        var path = uri.getPath();
        if (path.startsWith("/api/1/etf/holdings/"))
        {
            var isin = path.substring("/api/1/etf/holdings/".length());
            return switch (isin)
            {
                case WORLD_ETF -> json(200, "[" + row(isin, APPLE, "Apple", "45.5") + "," + row(isin, MICROSOFT,
                                "Microsoft", "30") + "," + row(isin, NVIDIA, "NVIDIA", "20") + ","
                                + row(isin, null, "Cash", "4.5") + "]");
                case ALL_WORLD_ETF -> json(200, "[" + row(isin, APPLE, "Apple", "40") + "," + row(isin, MICROSOFT,
                                "Microsoft", "35") + "," + row(isin, SAP, "SAP", "25") + "]");
                default -> json(404, "");
            };
        }
        if (path.startsWith("/api/1/equity/profile/"))
        {
            var isin = path.substring("/api/1/equity/profile/".length());
            return switch (isin)
            {
                case APPLE -> json(200, profile(isin, "Apple", "United States", "USD", "Technology"));
                case MICROSOFT -> json(200, profile(isin, "Microsoft", "United States", "USD", "Technology"));
                case NVIDIA -> json(200, profile(isin, "NVIDIA", "United States", "USD", "Technology"));
                case SAP -> json(200, profile(isin, "SAP", "Germany", "EUR", "Technology"));
                default -> json(404, "");
            };
        }
        if (path.equals("/api/1/equity/search"))
        {
            var query = uri.getQuery() == null ? "" : uri.getQuery();
            if (query.contains(WORLD_ETF) || query.contains(ALL_WORLD_ETF))
            {
                var isin = query.contains(WORLD_ETF) ? WORLD_ETF : ALL_WORLD_ETF;
                return json(200, "{\"count\":1,\"results\":[{\"isin\":\"" + isin
                                + "\",\"name\":\"Synthetic ETF\",\"ticker\":\"ETF.T\",\"type\":\"etf\",\"currency\":null}]}");
            }
            return json(200, "{\"count\":0,\"results\":[]}");
        }
        if (path.startsWith("/api/1/bonds/list"))
            return json(200, "{\"data\":[],\"total\":0,\"offset\":0,\"limit\":50}");
        if (path.startsWith("/api/1/equity/regions/"))
            return json(200, "[]");
        return json(404, "");
    }

    private static String row(String etf, String isin, String name, String percent)
    {
        return "{\"etf_symbol\":\"" + etf + "\",\"symbol\":" + (isin == null ? "null" : "\"" + name.toUpperCase() + "\"")
                        + ",\"name\":\"" + name + "\",\"isin\":" + (isin == null ? "null" : "\"" + isin + "\"")
                        + ",\"cusip\":null,\"percent\":" + percent
                        + ",\"share\":1,\"value\":1,\"assetType\":null,\"logo\":null,\"updated_at\":\"2026-07-01T00:00:00Z\"}";
    }

    private static String profile(String isin, String name, String country, String currency, String sector)
    {
        return "{\"isin\":\"" + isin + "\",\"ticker\":\"T\",\"name\":\"" + name + "\",\"logo\":null,\"country\":\""
                        + country + "\",\"currency\":\"" + currency + "\",\"employees\":1,\"sector\":\"" + sector
                        + "\",\"industry\":\"Invented industry\",\"ipo\":null,\"mcap\":1000.0,\"shares\":1.0,"
                        + "\"website\":null,\"wkn\":null,\"description\":\"Invented\"}";
    }

    private static Response json(int status, String body)
    {
        return RecordingHttp.json(status, body);
    }
}
