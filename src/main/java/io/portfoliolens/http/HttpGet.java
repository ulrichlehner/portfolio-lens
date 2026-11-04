package io.portfoliolens.http;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The only way Lens talks to other systems: one HTTP GET. There is no method for any other HTTP
 * verb, so no code path can write to Portfolio Performance or a provider. Implementations must
 * not follow redirects and must not log header values, which carry credentials.
 */
@FunctionalInterface
public interface HttpGet
{
    record Response(int status, String body, Map<String, List<String>> headers)
    {
        public Response
        {
            Objects.requireNonNull(body);
            headers = Map.copyOf(headers);
        }

        /** First value of a header, case-insensitive. */
        public Optional<String> header(String name)
        {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                            .flatMap(e -> e.getValue().stream()).findFirst();
        }

        @Override
        public String toString()
        {
            return "HTTP response [status=" + status + "]";
        }
    }

    Response get(URI uri, Map<String, String> headers) throws IOException;
}
