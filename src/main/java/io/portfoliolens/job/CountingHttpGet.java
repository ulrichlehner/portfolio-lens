package io.portfoliolens.job;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.portfoliolens.http.HttpGet;

/** Counts the requests of a run for the log; never records URLs or headers. */
final class CountingHttpGet implements HttpGet
{
    private final HttpGet delegate;
    private final AtomicInteger count = new AtomicInteger();

    CountingHttpGet(HttpGet delegate)
    {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException
    {
        count.incrementAndGet();
        return delegate.get(uri, headers);
    }

    int count()
    {
        return count.get();
    }
}
