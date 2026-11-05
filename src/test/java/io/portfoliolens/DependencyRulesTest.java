package io.portfoliolens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Keeps the port free of Portfolio Performance, Eclipse and Apache HttpClient code (JDK and Gson
 * only), and the calculation core free of anything but {@code java.*} and Gson (AGENTS.md, Rule 3; design D1).
 */
public class DependencyRulesTest
{
    private static final Path MAIN = Path.of("src/main/java/io/portfoliolens");
    private static final Pattern IMPORT = Pattern.compile("^import (static )?([\\w.]+)", Pattern.MULTILINE);

    private static List<String> violations(Path root, Pattern allowed) throws IOException
    {
        var result = new ArrayList<String>();
        try (var files = Files.walk(root))
        {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList())
            {
                var matcher = IMPORT.matcher(Files.readString(file));
                while (matcher.find())
                    if (!allowed.matcher(matcher.group(2)).lookingAt())
                        result.add(root.relativize(file) + ": " + matcher.group(2));
            }
        }
        return result;
    }

    @Test
    public void testCoreUsesOnlyJdkGsonAndLens() throws IOException
    {
        assertEquals(List.of(), violations(MAIN.resolve("core"),
                        Pattern.compile("(java\\.|com\\.google\\.gson\\.|io\\.portfoliolens\\.)")));
    }

    @Test
    public void testNoPortfolioPerformanceEclipseOrApacheCode() throws IOException
    {
        assertEquals(List.of(), violations(MAIN, Pattern.compile("(java\\.|javax\\.crypto\\.|com\\.google\\.gson\\.|io\\.portfoliolens\\.)")));
    }
}
