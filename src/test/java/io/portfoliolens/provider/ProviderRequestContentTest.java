package io.portfoliolens.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.CompositionInstrument;
import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.testing.RecordingHttp;

/**
 * Provider requests carry the instrument identifier and, for Eulerpool, the key header, and
 * nothing about the portfolio: no amounts, quantities or holding names (fund-compositions,
 * "Credential and portfolio privacy").
 */
public class ProviderRequestContentTest
{
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-03T12:00:00Z"), ZoneOffset.UTC);
    private static final ProviderAccount ACCOUNT = new ProviderAccount(EulerpoolFundCompositionProvider.ID, "local");
    private static final String KEY = "synthetic-key";
    private static final String HOLDING_NAME = "My Private Fund Holding";

    private static void assertNoPortfolioData(RecordingHttp.Request request)
    {
        var text = request.uri() + " " + request.headers();
        assertFalse(text.contains("Private"), text);
        assertFalse(text.contains("1234.56"), text);
        assertFalse(text.matches(".*[?&](shares|amount|value|quantity)=.*"), text);
    }

    @Test
    public void testEulerpoolRequestsCarryOnlyTheIdentifierAndKey() throws Exception
    {
        var http = new RecordingHttp(uri -> RecordingHttp.json(404, ""));
        var provider = new EulerpoolFundCompositionProvider(account -> Optional.of(KEY),
                        EulerpoolFundCompositionProvider.transport(http), CLOCK);
        var fund = new CompositionInstrument(HOLDING_NAME, Map.of("isin", "XX0000000017"));

        provider.getComposition(ACCOUNT, fund);
        provider.classify(ACCOUNT, fund);
        provider.profile(ACCOUNT, "XX0000000017");

        assertFalse(http.requests().isEmpty());
        for (var request : http.requests())
        {
            assertTrue(request.uri().toString().contains("XX0000000017"), request.uri().toString());
            assertEquals(Set.of("Authorization", "Accept"), request.headers().keySet());
            assertNoPortfolioData(request);
        }
    }

    @Test
    public void testRegisterRequestsCarryOnlyIdentifiersAndNoKey() throws Exception
    {
        var http = new RecordingHttp(uri -> RecordingHttp.json(200, "{\"data\":[],\"meta\":{\"pagination\":{\"total\":0}}}"));
        var register = new GleifCompanyRegister(GleifCompanyRegister.transport(http), () -> {
        }, CLOCK);

        register.lookup(List.of("XX0000000017", "XX0000000025"));

        assertFalse(http.requests().isEmpty());
        for (var request : http.requests())
        {
            assertEquals(Set.of("Accept"), request.headers().keySet());
            assertFalse(request.headers().toString().contains(KEY));
            assertNoPortfolioData(request);
        }
    }
}
