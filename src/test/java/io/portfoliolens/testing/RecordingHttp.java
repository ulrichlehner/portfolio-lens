package io.portfoliolens.testing;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.portfoliolens.http.HttpGet;

/** An {@link HttpGet} that records every request and answers from a function. */
public final class RecordingHttp implements HttpGet
{
    public record Request(URI uri, Map<String, String> headers)
    {
    }

    private final List<Request> requests = new ArrayList<>();
    private final Function<URI, Response> answers;

    public RecordingHttp(Function<URI, Response> answers)
    {
        this.answers = answers;
    }

    public static Response json(int status, String body)
    {
        return new Response(status, body, Map.of("Content-Type", List.of("application/json")));
    }

    @Override
    public synchronized Response get(URI uri, Map<String, String> headers) throws IOException
    {
        requests.add(new Request(uri, Map.copyOf(headers)));
        return answers.apply(uri);
    }

    public synchronized List<Request> requests()
    {
        return List.copyOf(requests);
    }
}
