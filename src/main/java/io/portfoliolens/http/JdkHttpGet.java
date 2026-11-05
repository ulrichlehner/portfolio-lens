package io.portfoliolens.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** {@link HttpGet} on the JDK HTTP client: bounded timeouts, no redirects, no retries. */
public final class JdkHttpGet implements HttpGet
{
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final String userAgent;

    public JdkHttpGet(String userAgent)
    {
        this.userAgent = Objects.requireNonNull(userAgent);
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException
    {
        var request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("User-Agent", userAgent).GET();
        headers.forEach(request::header);
        try
        {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), Objects.requireNonNullElse(response.body(), ""),
                            response.headers().map());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("request interrupted");
        }
    }
}
