package io.portfoliolens.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** The stand-in behaves like PP towards requests that PP refuses. */
public class FakePpServerTest
{
    private static int status(HttpRequest request) throws Exception
    {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    public void testRefusesOriginHeaderAndAcceptsPlainRequests() throws Exception
    {
        try (var server = new FakePpServer("t").serveStandard())
        {
            var uri = URI.create(server.baseUrl() + "/v1/version");
            assertEquals(200, status(HttpRequest.newBuilder(uri).GET().build()));
            assertEquals(403, status(HttpRequest.newBuilder(uri).header("Origin", "https://example.com").GET().build()));
            assertEquals(401, status(HttpRequest.newBuilder(URI.create(server.baseUrl() + "/v1/files")).GET().build()));
        }
    }

    @Test
    public void testRefusesNonLoopbackHostHeader() throws Exception
    {
        try (var server = new FakePpServer("t").serveStandard();
                        var socket = new Socket(InetAddress.getLoopbackAddress(), server.baseUrl().getPort()))
        {
            // the JDK client does not let callers set Host, so the request is written by hand
            socket.getOutputStream().write("GET /v1/version HTTP/1.1\r\nHost: host.docker.internal:5712\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertEquals("HTTP/1.1 403 Forbidden", reader.readLine());
        }
    }
}
