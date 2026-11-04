package io.portfoliolens.core;

import java.util.Map;
import java.util.Objects;

/**
 * An instrument held in the analysed Portfolio Performance file, as read from the REST API.
 * Identity is the PP uuid. The identifiers use the {@link CompositionInstrument} keys (for
 * example {@code isin}) and are what provider requests may carry; the name is for display only.
 */
public record HeldInstrument(String uuid, String name, String currencyCode, Map<String, String> identifiers)
{
    public HeldInstrument
    {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(name);
        identifiers = Map.copyOf(identifiers);
    }

    /** The instrument as a source row for classification and composition lookups. */
    public CompositionInstrument asCompositionInstrument()
    {
        return new CompositionInstrument(name, identifiers);
    }
}
