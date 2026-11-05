package io.portfoliolens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Test data stays synthetic (AGENTS.md, Rule 2): every ISIN-shaped string in the test sources and
 * fixtures is either a well-known public instrument or an obviously invented code.
 */
public class FixturePrivacyTest
{
    /** Widely published instruments, used only as identifiers with invented amounts. */
    static final Set<String> PUBLIC_ISINS = Set.of(
                    "IE00B4L5Y983", // iShares Core MSCI World UCITS ETF
                    "IE00BK5BQT80", // Vanguard FTSE All-World UCITS ETF (Acc)
                    "US0378331005", // Apple Inc.
                    "DE0007164600", // SAP SE
                    "US5949181045", // Microsoft Corp.
                    "US67066G1040"); // NVIDIA Corp.

    private static final Pattern ISIN = Pattern.compile("\\b[A-Z]{2}[A-Z0-9]{9}[0-9]\\b");
    private static final Pattern INVENTED = Pattern.compile("[A-Z]{2}0{5,}[0-9]+|SYNTHETIC[0-9]+");

    @Test
    public void testOnlyPublicOrInventedIsins() throws IOException
    {
        var found = new TreeSet<String>();
        for (var root : new Path[] { Path.of("src/test"), Path.of("src/main") })
            try (var files = Files.walk(root))
            {
                for (var file : files.filter(Files::isRegularFile).toList())
                {
                    var matcher = ISIN.matcher(Files.readString(file));
                    while (matcher.find())
                        if (!PUBLIC_ISINS.contains(matcher.group()) && !INVENTED.matcher(matcher.group()).matches())
                            found.add(root.relativize(file) + ": " + matcher.group());
                }
            }
        assertEquals(Set.of(), found);
    }
}
