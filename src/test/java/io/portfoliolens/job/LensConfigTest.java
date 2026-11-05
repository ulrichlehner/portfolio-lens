package io.portfoliolens.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Configuration sources, secrets and validation (lens-job and composition-provider-settings specs). */
public class LensConfigTest
{
    private static final String TOKEN = "synthetic-pp-token";
    private static final String KEY = "synthetic-provider-key";

    @TempDir
    Path temp;

    private Map<String, String> minimal()
    {
        var env = new HashMap<String, String>();
        env.put(LensConfig.PP_TOKEN, TOKEN);
        env.put(LensConfig.OUTPUT_DIR, temp.resolve("out").toString());
        return env;
    }

    private LensConfig.Result load(Map<String, String> env)
    {
        return LensConfig.load(env, Optional.empty(), temp, true);
    }

    private static String errors(LensConfig.Result result)
    {
        return result.errors().toString();
    }

    @Test
    public void testDefaults()
    {
        var settings = load(minimal()).settings().orElseThrow();
        assertEquals("http://127.0.0.1:5712", settings.ppUrl().toString());
        assertEquals(Optional.of(TOKEN), settings.ppToken());
        assertFalse(settings.localStorageConfirmed());
        assertTrue(settings.registerLookup());
        assertEquals(20000, settings.requestBudget());
        assertEquals(Duration.ofMinutes(2), settings.runTimeout());
        assertEquals(temp.resolve(".cache/portfolio-lens"), settings.cacheDir());
    }

    private LensConfig.Result withLink(String url, String label)
    {
        var env = minimal();
        if (url != null)
            env.put(LensConfig.APP_LINK_URL, url);
        if (label != null)
            env.put(LensConfig.APP_LINK_LABEL, label);
        return load(env);
    }

    @Test
    public void testAppLinkIsOptional()
    {
        assertTrue(withLink(null, null).settings().orElseThrow().appLink().isEmpty());
    }

    @Test
    public void testAppLinkWithDefaultAndCustomLabel()
    {
        var link = withLink("https://example.org/", null).settings().orElseThrow().appLink().orElseThrow();
        assertEquals("https://example.org/", link.url());
        assertEquals("Portfolio Performance", link.label());
        var http = withLink("HTTP://example.org:8080/app", "  Back to PP ").settings().orElseThrow().appLink()
                        .orElseThrow();
        assertEquals("HTTP://example.org:8080/app", http.url());
        assertEquals("Back to PP", http.label());
    }

    @Test
    public void testAppLinkRejectsUnsafeOrIncompleteUrls()
    {
        for (var url : new String[] { "javascript:alert(1)", "ftp://example.org/", "/lens/", "example.org",
                        "https://", "https://user:secret@example.org/", "https://user@example.org/", "https://exa mple.org/" })
        {
            var result = withLink(url, null);
            assertTrue(result.settings().isEmpty(), url);
            assertTrue(errors(result).contains(LensConfig.APP_LINK_URL), url);
            assertFalse(errors(result).contains("secret") || errors(result).contains("alert"), errors(result));
        }
    }

    @Test
    public void testAppLinkLabelRules()
    {
        assertTrue(withLink("https://example.org/", "x".repeat(60)).settings().isPresent());
        for (var label : new String[] { "x".repeat(61), "two\nlines", "two\rlines" })
        {
            var result = withLink("https://example.org/", label);
            assertTrue(result.settings().isEmpty());
            assertTrue(errors(result).contains(LensConfig.APP_LINK_LABEL), errors(result));
        }
        var orphan = withLink(null, "Back");
        assertTrue(orphan.settings().isEmpty());
        assertTrue(errors(orphan).contains(LensConfig.APP_LINK_LABEL), errors(orphan));
    }

    @Test
    public void testAppLinkFromConfigFileLikeEnvironment() throws IOException
    {
        var file = Files.writeString(temp.resolve("lens.properties"),
                        LensConfig.APP_LINK_URL + "=https://example.org/\n" + LensConfig.APP_LINK_LABEL + "=From file\n");
        var env = minimal();
        var fromFile = LensConfig.load(env, Optional.of(file), temp, true).settings().orElseThrow().appLink()
                        .orElseThrow();
        assertEquals("From file", fromFile.label());
        env.put(LensConfig.APP_LINK_LABEL, "From environment");
        assertEquals("From environment", LensConfig.load(env, Optional.of(file), temp, true).settings().orElseThrow()
                        .appLink().orElseThrow().label());
        var bad = Files.writeString(temp.resolve("bad.properties"), LensConfig.APP_LINK_URL + "=ftp://example.org/\n");
        assertTrue(errors(LensConfig.load(minimal(), Optional.of(bad), temp, true)).contains(LensConfig.APP_LINK_URL));
    }

    @Test
    public void testAppLinkIsPartOfTheFingerprint()
    {
        var none = withLink(null, null).settings().orElseThrow().fingerprintPart();
        var one = withLink("https://example.org/", null).settings().orElseThrow().fingerprintPart();
        var label = withLink("https://example.org/", "Other").settings().orElseThrow().fingerprintPart();
        assertFalse(none.equals(one) || one.equals(label) || none.equals(label));
    }

    @Test
    public void testMissingTokenAndOutputAreNamed()
    {
        var result = load(Map.of());
        assertTrue(result.settings().isEmpty());
        assertTrue(errors(result).contains(LensConfig.PP_TOKEN), errors(result));
        assertTrue(errors(result).contains(LensConfig.OUTPUT_DIR), errors(result));
        // check does not need the output directory
        var check = LensConfig.load(Map.of(LensConfig.PP_TOKEN, TOKEN), Optional.empty(), temp, false);
        assertTrue(check.settings().isPresent(), errors(check));
    }

    @Test
    public void testSecretFromFileWithoutTrailingLineBreak() throws IOException
    {
        var file = Files.writeString(temp.resolve("token"), TOKEN + "\n");
        var env = minimal();
        env.remove(LensConfig.PP_TOKEN);
        env.put(LensConfig.PP_TOKEN + LensConfig.FILE_SUFFIX, file.toString());
        assertEquals(Optional.of(TOKEN), load(env).settings().orElseThrow().ppToken());
    }

    @Test
    public void testSecretBothDirectAndFileIsAnError() throws IOException
    {
        var file = Files.writeString(temp.resolve("token"), TOKEN);
        var env = minimal();
        env.put(LensConfig.PP_TOKEN + LensConfig.FILE_SUFFIX, file.toString());
        var result = load(env);
        assertTrue(result.settings().isEmpty());
        assertTrue(errors(result).contains(LensConfig.PP_TOKEN + LensConfig.FILE_SUFFIX), errors(result));
        assertFalse(errors(result).contains(TOKEN));
    }

    @Test
    public void testUnreadableSecretFileIsNamed()
    {
        var env = minimal();
        env.put(LensConfig.EULERPOOL_API_KEY + LensConfig.FILE_SUFFIX, temp.resolve("missing").toString());
        var result = load(env);
        assertTrue(errors(result).contains(LensConfig.EULERPOOL_API_KEY + LensConfig.FILE_SUFFIX), errors(result));
    }

    @Test
    public void testInvalidKeyNamesSettingButNotTheKey()
    {
        var env = minimal();
        env.put(LensConfig.EULERPOOL_API_KEY, "synthetic key\nwith-break");
        var result = load(env);
        assertTrue(result.settings().isEmpty());
        assertTrue(errors(result).contains(LensConfig.EULERPOOL_API_KEY), errors(result));
        assertFalse(errors(result).contains("synthetic key"), errors(result));
    }

    @Test
    public void testEnvironmentWinsOverPropertiesFile() throws IOException
    {
        var file = Files.writeString(temp.resolve("lens.properties"),
                        LensConfig.PP_FILE + "=from-file\n" + LensConfig.REQUEST_BUDGET + "=500\n");
        var env = minimal();
        env.put(LensConfig.PP_FILE, "from-env");
        var settings = LensConfig.load(env, Optional.of(file), temp, true).settings().orElseThrow();
        assertEquals(Optional.of("from-env"), settings.ppFile());
        assertEquals(500, settings.requestBudget());
    }

    @Test
    public void testNonLoopbackUrlIsAConfigurationError()
    {
        var env = minimal();
        env.put(LensConfig.PP_URL, "http://pp.example.com:5712");
        assertTrue(errors(load(env)).contains(LensConfig.PP_URL));
    }

    @Test
    public void testConfirmationAcceptsOnlyTrueAndFalse()
    {
        for (var value : new String[] { "yes", "TRUE ", "1", "True" })
        {
            var env = minimal();
            env.put(LensConfig.LOCAL_STORAGE_CONFIRMED, value);
            var result = load(env);
            assertTrue(result.settings().isEmpty(), value);
            assertTrue(errors(result).contains(LensConfig.LOCAL_STORAGE_CONFIRMED + ": must be true or false"),
                            errors(result));
        }
        var env = minimal();
        env.put(LensConfig.LOCAL_STORAGE_CONFIRMED, "true");
        assertTrue(load(env).settings().orElseThrow().localStorageConfirmed());
    }

    @Test
    public void testUnconfirmedKeyState()
    {
        var env = minimal();
        env.put(LensConfig.EULERPOOL_API_KEY, KEY);
        var settings = load(env).settings().orElseThrow();
        assertTrue(settings.unconfirmedKey());
        assertFalse(settings.providerEnabled());
        assertFalse(settings.toString().contains(KEY));
        env.put(LensConfig.LOCAL_STORAGE_CONFIRMED, "true");
        settings = load(env).settings().orElseThrow();
        assertFalse(settings.unconfirmedKey());
        assertTrue(settings.providerEnabled());
    }

    @Test
    public void testInvalidNumbersAndDurations()
    {
        var env = minimal();
        env.put(LensConfig.REQUEST_BUDGET, "-5");
        env.put(LensConfig.RUN_TIMEOUT, "two minutes");
        env.put(LensConfig.REPORTING_CURRENCY, "euro");
        var text = errors(load(env));
        assertTrue(text.contains(LensConfig.REQUEST_BUDGET), text);
        assertTrue(text.contains(LensConfig.RUN_TIMEOUT), text);
        assertTrue(text.contains(LensConfig.REPORTING_CURRENCY), text);
    }
}
