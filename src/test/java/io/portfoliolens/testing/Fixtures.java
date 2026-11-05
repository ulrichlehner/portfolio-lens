package io.portfoliolens.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads synthetic fixtures from {@code src/test/resources/fixtures}. */
public final class Fixtures
{
    private Fixtures()
    {
    }

    public static String read(String path)
    {
        try (var in = Fixtures.class.getResourceAsStream("/fixtures/" + path))
        {
            if (in == null)
                throw new IllegalArgumentException("no fixture " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
