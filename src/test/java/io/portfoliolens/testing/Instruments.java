package io.portfoliolens.testing;

import java.util.Map;
import java.util.UUID;

import io.portfoliolens.core.HeldInstrument;

/** Synthetic held instruments with a fresh uuid each, like distinct PP securities. */
public final class Instruments
{
    private Instruments()
    {
    }

    public static HeldInstrument instrument(String name, String currency)
    {
        return new HeldInstrument(UUID.randomUUID().toString(), name, currency, Map.of());
    }

    public static HeldInstrument instrument(String name, String currency, Map<String, String> identifiers)
    {
        return new HeldInstrument(UUID.randomUUID().toString(), name, currency, identifiers);
    }
}
