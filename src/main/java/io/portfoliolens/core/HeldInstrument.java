package io.portfoliolens.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An instrument held in the analysed Portfolio Performance file, as read from the REST API.
 * Identity is the PP uuid. Identifiers use the {@link CompositionInstrument} keys (for example
 * {@link InstrumentEvidence#ISIN}); keys starting with {@code display:} are shown but never sent to a
 * provider. The name is for display only.
 */
public record HeldInstrument(String uuid, String name, String currencyCode, Map<String, String> identifiers)
{
    public HeldInstrument
    {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(name);
        identifiers = Map.copyOf(identifiers);
    }

    /** Prefix of identifier keys that are kept for display and never sent to a provider. */
    public static final String DISPLAY_PREFIX = "display:";

    /** The instrument as a source row for classification and composition lookups. */
    public CompositionInstrument asCompositionInstrument()
    {
        var requestable = new HashMap<String, String>();
        identifiers.forEach((key, value) -> {
            if (!key.startsWith(DISPLAY_PREFIX))
                requestable.put(key, value);
        });
        return new CompositionInstrument(name, requestable);
    }
}
