package io.portfoliolens.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.provider.CompositionCredentials.Mode;

public class CompositionCredentialsTest
{
    private static final ProviderAccount ACCOUNT = new ProviderAccount("EULERPOOL", "local");
    private static final String KEY = "synthetic-Key_0123.abc~+/=";

    @Test
    public void testValidKeyIsReturned()
    {
        var credentials = new CompositionCredentials(account -> Optional.of(KEY));
        assertEquals(Optional.of(KEY), credentials.getBearerToken(ACCOUNT));
        assertEquals(Mode.CONFIGURED, credentials.state(ACCOUNT).mode());
    }

    @Test
    public void testMissingKeyIsUnconfigured()
    {
        var credentials = new CompositionCredentials(account -> Optional.empty());
        assertEquals(Optional.empty(), credentials.getBearerToken(ACCOUNT));
        assertEquals(Mode.UNCONFIGURED, credentials.state(ACCOUNT).mode());
    }

    @Test
    public void testInvalidKeysAreRefused()
    {
        for (var invalid : new String[] { "with space", "line\nbreak", "tab\t", "", "a".repeat(8193), "quote\"" })
        {
            var credentials = new CompositionCredentials(account -> Optional.of(invalid));
            assertEquals(Optional.empty(), credentials.getBearerToken(ACCOUNT));
            assertEquals(Mode.UNAVAILABLE, credentials.state(ACCOUNT).mode());
            assertFalse(CompositionCredentials.isValid(invalid));
        }
        assertTrue(CompositionCredentials.isValid(KEY));
    }

    @Test
    public void testUnreadableStoreIsUnavailableWithoutLeakingTheKey()
    {
        var credentials = new CompositionCredentials(account -> {
            throw new IOException("cannot read " + KEY);
        });
        assertEquals(Optional.empty(), credentials.getBearerToken(ACCOUNT));
        var state = credentials.state(ACCOUNT);
        assertEquals(Mode.UNAVAILABLE, state.mode());
        assertFalse(state.toString().contains(KEY));
        assertFalse(credentials.toString().contains(KEY));
    }
}
