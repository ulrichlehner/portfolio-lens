package io.portfoliolens.provider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.portfoliolens.core.ProviderAccount;

/**
 * Provider credentials read from the configuration. Lens never writes a key: the store is
 * read-only, and a key that fails validation is treated as unavailable. No backend exception
 * escapes this boundary, and the key never appears in a state or message.
 */
public final class CompositionCredentials
{
    /** Read-only source of a provider key, for example the environment or a secret file. */
    @FunctionalInterface
    public interface KeyStore
    {
        Optional<String> read(ProviderAccount account) throws IOException;
    }

    public enum Mode
    {
        UNCONFIGURED, CONFIGURED, UNAVAILABLE
    }

    /** Safe status for the job and the page; revision is not derived from the secret. */
    public record State(Mode mode, long revision)
    {
    }

    private static final class Entry
    {
        private Mode mode = Mode.UNCONFIGURED;
        private long revision;
    }

    private final KeyStore store;
    private final Map<ProviderAccount, Entry> entries = new HashMap<>();

    public CompositionCredentials(KeyStore store)
    {
        this.store = Objects.requireNonNull(store);
    }

    public synchronized Optional<String> getBearerToken(ProviderAccount account)
    {
        var entry = entry(account);
        try
        {
            var token = store.read(account);
            if (token.isPresent() && !isValid(token.get()))
                throw new IOException("invalid key");
            entry.mode = token.isPresent() ? Mode.CONFIGURED : Mode.UNCONFIGURED;
            return token;
        }
        catch (IOException | RuntimeException e)
        {
            entry.mode = Mode.UNAVAILABLE;
            return Optional.empty();
        }
    }

    public synchronized State state(ProviderAccount account)
    {
        getBearerToken(account);
        var entry = entry(account);
        return new State(entry.mode, entry.revision);
    }

    private Entry entry(ProviderAccount account)
    {
        return entries.computeIfAbsent(Objects.requireNonNull(account), ignored -> new Entry());
    }

    /** The accepted key alphabet: printable token characters, no whitespace or line breaks. */
    public static boolean isValid(String token)
    {
        return token != null && token.length() <= 8192 && token.matches("[A-Za-z0-9._~+/-]+=*");
    }
}
