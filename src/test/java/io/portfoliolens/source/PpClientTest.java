package io.portfoliolens.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.portfoliolens.http.JdkHttpGet;
import io.portfoliolens.source.PpException.Kind;
import io.portfoliolens.testing.FakePpServer;
import io.portfoliolens.testing.Fixtures;

/** Contract tests of the PP REST reader against synthetic responses shaped by PP's openapi.yaml. */
public class PpClientTest
{
    private static final String TOKEN = "synthetic-pp-token";

    private static PpClient client(FakePpServer server)
    {
        return new PpClient(server.baseUrl(), TOKEN, new JdkHttpGet("PortfolioLens/test"));
    }

    @Test
    public void testOnlyGetWithoutOriginAndTokenExceptForVersion() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serveStandard())
        {
            var client = client(server);
            client.version();
            client.files();
            client.holdings("main", Optional.empty());
            client.instruments("main");

            assertEquals(4, server.requests().size());
            for (var request : server.requests())
            {
                assertEquals("GET", request.method());
                assertFalse(request.hasHeader("Origin"));
            }
            assertFalse(server.requests().get(0).hasHeader("Authorization"));
            assertTrue(server.requests().stream().skip(1).allMatch(r -> r.hasHeader("Authorization")));
        }
    }

    @Test
    public void testNonLoopbackUrlIsRefusedBeforeAnyRequest()
    {
        for (var url : List.of("http://pp.example.com:5712", "https://127.0.0.1:5712", "http://10.0.0.2:5712",
                        "http://host.docker.internal:5712", "http://127.0.0.1:5712/v1"))
            assertThrows(IllegalArgumentException.class, () -> PpClient.validateBaseUrl(URI.create(url)), url);
        for (var url : List.of("http://127.0.0.1:5712", "http://localhost:5712/", "http://[::1]:5712"))
            PpClient.validateBaseUrl(URI.create(url));
    }

    @Test
    public void testVersionToleratesUnknownAndMissingOptionalFields() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serve("/v1/version", "pp/version.json"))
        {
            var version = client(server).version();
            assertEquals("1.2.0", version.apiVersion());
            assertEquals(1, version.major());
            assertEquals(Optional.of("0.89.0"), version.applicationVersion());
        }
        try (var server = new FakePpServer(TOKEN).serve("/v1/version", "pp/version-without-application.json"))
        {
            assertEquals(Optional.empty(), client(server).version().applicationVersion());
        }
        try (var server = new FakePpServer(TOKEN).serve("/v1/version", "pp/version-v2.json"))
        {
            assertEquals(2, client(server).version().major());
        }
    }

    @Test
    public void testUnreachablePp() throws Exception
    {
        int port;
        try (var socket = new ServerSocket(0))
        {
            port = socket.getLocalPort();
        }
        var client = new PpClient(URI.create("http://127.0.0.1:" + port), TOKEN, new JdkHttpGet("t"));
        var e = assertThrows(PpException.class, client::version);
        assertEquals(Kind.UNREACHABLE, e.kind());
        assertTrue(e.getMessage().contains("not running"));
    }

    @Test
    public void testRejectedTokenIsNamedWithoutPrintingIt() throws Exception
    {
        try (var server = new FakePpServer("another-token").serveStandard())
        {
            var e = assertThrows(PpException.class, () -> client(server).files());
            assertEquals(Kind.UNAUTHORIZED, e.kind());
            assertFalse(e.getMessage().contains(TOKEN));
        }
    }

    @Test
    public void testProblemTypesMapToDistinctKinds() throws Exception
    {
        try (var server = new FakePpServer(TOKEN)
                        .answer("/v1/files/open/holdings", 409, Fixtures.read("pp/problem-409-file-not-open.json"))
                        .answer("/v1/files/twice/holdings", 409, Fixtures.read("pp/problem-409-ambiguous-alias.json"))
                        .answer("/v1/files/other/holdings", 409, "{\"type\": \"https://example.invalid/new\"}")
                        .answer("/v1/files/broken/holdings", 500, "not json"))
        {
            var client = client(server);
            assertEquals(Kind.FILE_NOT_OPEN, assertThrows(PpException.class,
                            () -> client.holdings("open", Optional.empty())).kind());
            assertEquals(Kind.AMBIGUOUS_ALIAS, assertThrows(PpException.class,
                            () -> client.holdings("twice", Optional.empty())).kind());
            assertEquals(Kind.HTTP_ERROR, assertThrows(PpException.class,
                            () -> client.holdings("other", Optional.empty())).kind());
            assertEquals(Kind.HTTP_ERROR, assertThrows(PpException.class,
                            () -> client.holdings("broken", Optional.empty())).kind());
            assertEquals(Kind.NOT_FOUND, assertThrows(PpException.class,
                            () -> client.holdings("unknown", Optional.empty())).kind());
        }
    }

    @Test
    public void testFilesIgnoreUnknownFields() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serve("/v1/files", "pp/files-two.json"))
        {
            var files = client(server).files();
            assertEquals(2, files.size());
            assertEquals(Optional.of("main"), files.get(0).alias());
            assertEquals(Optional.empty(), files.get(1).alias());
            assertFalse(files.get(0).toString().contains("/synthetic"));
        }
    }

    @Test
    public void testHoldingsMapping() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serveStandard())
        {
            var holdings = client(server).holdings("main", Optional.empty());
            assertEquals(LocalDate.of(2026, 7, 20), holdings.date());
            assertEquals("EUR", holdings.reportingCurrency());
            assertEquals(7, holdings.items().size());
            var us = holdings.items().get(1);
            assertTrue(us.isInstrument());
            assertEquals(new BigDecimal("1840.00"), us.valuation().value());
            assertEquals("USD", us.localValuation().orElseThrow().currency());
            assertEquals(Optional.of(LocalDate.of(2026, 7, 17)), us.price().orElseThrow().date());
            assertTrue(holdings.items().get(5).isCashAccount());
            var wallet = holdings.items().get(6);
            assertFalse(wallet.isInstrument() || wallet.isCashAccount());
            assertEquals("crypto-wallet", wallet.type());
        }
    }

    @Test
    public void testReportingCurrencyIsPassedAsQueryParameter() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serve("/v1/files/main/holdings?reportingCurrency=CHF",
                        "pp/holdings.json"))
        {
            client(server).holdings("main", Optional.of("CHF"));
            assertEquals("/v1/files/main/holdings?reportingCurrency=CHF", server.requests().get(0).path());
        }
    }

    @Test
    public void testMissingRequiredFieldNamesEndpointAndField() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serve("/v1/files/main/holdings", "pp/holdings-missing-valuation.json"))
        {
            var e = assertThrows(PpException.class, () -> client(server).holdings("main", Optional.empty()));
            assertEquals(Kind.MALFORMED, e.kind());
            assertTrue(e.getMessage().contains("holdings"), e.getMessage());
            assertTrue(e.getMessage().contains("'valuation'"), e.getMessage());
        }
    }

    @Test
    public void testInstrumentsWithAbsentOptionalFieldsAndNullCurrency() throws Exception
    {
        try (var server = new FakePpServer(TOKEN).serveStandard())
        {
            var instruments = client(server).instruments("main");
            assertEquals(6, instruments.size());
            assertEquals(Optional.of("IE00B4L5Y983"), instruments.get(0).isin());
            assertEquals(Optional.of("865985"), instruments.get(1).wkn());
            assertEquals(Optional.empty(), instruments.get(4).isin());
            assertEquals(Optional.empty(), instruments.get(5).currencyCode());
        }
    }
}
