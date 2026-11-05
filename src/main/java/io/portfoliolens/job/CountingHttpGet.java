package io.portfoliolens.job;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.portfoliolens.http.HttpGet;

/**
 * Counts the provider and register requests of a run by type and outcome for the log. At FINE it
 * also logs one line per request with type, outcome, HTTP status and the ISIN it asked for; it never
 * logs URLs, other query values or headers.
 */
final class CountingHttpGet implements HttpGet
{
    enum Type
    {
        COMPOSITION("fund composition"), PROFILE("company profile"), SEARCH("classification search"),
        REVENUE("revenue split"), REGISTER("GLEIF register"), OTHER("other");

        private final String label;

        Type(String label)
        {
            this.label = label;
        }
    }

    enum Outcome
    {
        FOUND, NOT_FOUND, FAILED
    }

    private static final Logger LOG = Logger.getLogger(CountingHttpGet.class.getName());
    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final HttpGet delegate;
    private final AtomicInteger count = new AtomicInteger();
    private final Map<Type, int[]> counts = new TreeMap<>();

    CountingHttpGet(HttpGet delegate)
    {
        this.delegate = Objects.requireNonNull(delegate);
    }

    static Type type(URI uri)
    {
        var host = uri.getHost() == null ? "" : uri.getHost();
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (host.endsWith("gleif.org"))
            return Type.REGISTER;
        if (path.contains("/etf/holdings/"))
            return Type.COMPOSITION;
        if (path.contains("/equity/profile/"))
            return Type.PROFILE;
        if (path.contains("/equity/search") || path.contains("/bonds/list"))
            return Type.SEARCH;
        if (path.contains("/equity/regions/"))
            return Type.REVENUE;
        return Type.OTHER;
    }

    /** An answer that carries no data counts as not found, even with status 200. */
    static Outcome outcome(Response response)
    {
        var status = response.status();
        if (status == 204 || status == 404)
            return Outcome.NOT_FOUND;
        if (status != 200)
            return Outcome.FAILED;
        var body = response.body().replace(" ", "");
        if (body.isEmpty() || body.equals("[]") || body.contains("\"count\":0,") || body.contains("\"total\":0"))
            return Outcome.NOT_FOUND;
        return Outcome.FOUND;
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException
    {
        count.incrementAndGet();
        var type = type(uri);
        try
        {
            var response = delegate.get(uri, headers);
            var outcome = outcome(response);
            add(type, outcome);
            if (LOG.isLoggable(Level.FINE))
                LOG.fine("request " + type.label + " " + isin(uri) + ": "
                                + outcome.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " (HTTP "
                                + response.status() + ")");
            return response;
        }
        catch (IOException | RuntimeException e)
        {
            add(type, Outcome.FAILED);
            if (LOG.isLoggable(Level.FINE))
                LOG.fine("request " + type.label + " " + isin(uri) + ": failed (" + e.getClass().getSimpleName() + ")");
            throw e;
        }
    }

    /** The ISIN a request names, or "-"; nothing else of the URL. */
    static String isin(URI uri)
    {
        var matcher = ISIN.matcher(uri.getRawPath() + "?" + (uri.getRawQuery() == null ? "" : uri.getRawQuery()));
        return matcher.find() ? matcher.group() : "-";
    }

    private synchronized void add(Type type, Outcome outcome)
    {
        counts.computeIfAbsent(type, ignored -> new int[Outcome.values().length])[outcome.ordinal()]++;
    }

    int count()
    {
        return count.get();
    }

    /** One line, for example "company profile 120 (found 110, not found 8, failed 2)". */
    synchronized String summary()
    {
        if (counts.isEmpty())
            return "no provider or register requests";
        var parts = new java.util.ArrayList<String>();
        counts.forEach((type, values) -> parts.add(type.label + " " + (values[0] + values[1] + values[2]) + " (found "
                        + values[0] + ", not found " + values[1] + ", failed " + values[2] + ")"));
        return "requests: " + String.join("; ", parts);
    }
}
