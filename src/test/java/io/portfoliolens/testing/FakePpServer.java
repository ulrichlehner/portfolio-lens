package io.portfoliolens.testing;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for the PP REST API on the JDK HTTP server, for tests only. Like PP it binds the
 * loopback interface, refuses a non-loopback Host header and any Origin header, requires the
 * bearer token except for {@code /v1/version}, and records every request.
 */
public final class FakePpServer implements AutoCloseable
{
    public record Request(String method, String path, Map<String, List<String>> headers)
    {
        public boolean hasHeader(String name)
        {
            return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
        }
    }

    private record Answer(int status, String body)
    {
    }

    private static final Pattern LOOPBACK_HOST = Pattern.compile("(127(\\.\\d{1,3}){3}|localhost|\\[::1\\])(:\\d+)?",
                    Pattern.CASE_INSENSITIVE);
    private static final String PROBLEMS = "https://portfolio-performance.info/rest/problems/";

    private final HttpServer server;
    private final String token;
    private final Map<String, Answer> answers = new HashMap<>();
    private final List<Request> requests = new ArrayList<>();

    public FakePpServer(String token) throws IOException
    {
        this.token = token;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI baseUrl()
    {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Answers GET {@code path} (with query) with the fixture under {@code fixtures/}. */
    public FakePpServer serve(String path, String fixture)
    {
        return answer(path, 200, Fixtures.read(fixture));
    }

    public synchronized FakePpServer answer(String path, int status, String body)
    {
        answers.put(path, new Answer(status, body));
        return this;
    }

    /** The id of the file in {@code pp/files-one.json}; its alias is {@code main}. */
    public static final String FILE_ID = "5f3c1e2a-0b7d-4a1e-9c2f-000000000001";

    /** The standard synthetic file with its holdings and instruments, by alias and by id. */
    public FakePpServer serveStandard()
    {
        serve("/v1/version", "pp/version.json").serve("/v1/files", "pp/files-one.json");
        for (var file : new String[] { "main", FILE_ID })
            serve("/v1/files/" + file + "/holdings", "pp/holdings.json").serve("/v1/files/" + file + "/instruments",
                            "pp/instruments.json");
        return this;
    }

    public synchronized List<Request> requests()
    {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        var path = exchange.getRequestURI().getRawPath()
                        + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
        Answer answer;
        synchronized (this)
        {
            requests.add(new Request(exchange.getRequestMethod(), path, Map.copyOf(exchange.getRequestHeaders())));
            var host = exchange.getRequestHeaders().getFirst("Host");
            var authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (host == null || !LOOPBACK_HOST.matcher(host).matches())
                answer = problem(403, "forbidden-host");
            else if (exchange.getRequestHeaders().getFirst("Origin") != null)
                answer = problem(403, "browser-origin-forbidden");
            else if (!"GET".equals(exchange.getRequestMethod()))
                answer = problem(405, "method-not-allowed");
            else if (!"/v1/version".equals(path) && !("Bearer " + token).equals(authorization))
                answer = new Answer(401, Fixtures.read("pp/problem-401.json"));
            else
                answer = answers.getOrDefault(path, problem(404, "not-found"));
        }
        var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                        answer.status() == 200 ? "application/json" : "application/problem+json");
        exchange.sendResponseHeaders(answer.status(), bytes.length);
        try (var out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }

    private static Answer problem(int status, String type)
    {
        return new Answer(status, "{\"type\": \"" + PROBLEMS + type + "\", \"title\": \"" + type + "\", \"status\": "
                        + status + "}");
    }

    @Override
    public void close()
    {
        server.stop(0);
    }
}
