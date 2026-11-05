package io.portfoliolens.job;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.portfoliolens.provider.CompositionCredentials;
import io.portfoliolens.source.PpClient;

/**
 * The configuration of one run (design D5): environment variables, optionally a properties file
 * named on the command line (the environment wins), and secrets either directly or from a file
 * named by the {@code _FILE} variant. Error messages name settings, never values.
 */
public final class LensConfig
{
    public static final String PP_URL = "LENS_PP_URL";
    public static final String PP_TOKEN = "LENS_PP_TOKEN";
    public static final String PP_FILE = "LENS_PP_FILE";
    public static final String REPORTING_CURRENCY = "LENS_REPORTING_CURRENCY";
    public static final String EULERPOOL_API_KEY = "LENS_EULERPOOL_API_KEY";
    public static final String LOCAL_STORAGE_CONFIRMED = "LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED";
    public static final String REGISTER_LOOKUP = "LENS_REGISTER_LOOKUP";
    public static final String REQUEST_BUDGET = "LENS_REQUEST_BUDGET";
    public static final String RUN_TIMEOUT = "LENS_RUN_TIMEOUT";
    public static final String CACHE_DIR = "LENS_CACHE_DIR";
    public static final String OUTPUT_DIR = "LENS_OUTPUT_DIR";
    public static final String APP_LINK_URL = "LENS_APP_LINK_URL";
    public static final String APP_LINK_LABEL = "LENS_APP_LINK_LABEL";
    public static final String FILE_SUFFIX = "_FILE";

    /** The exact setting to add when a provider key is configured without the confirmation. */
    public static final String CONFIRMATION_HINT = LOCAL_STORAGE_CONFIRMED + "=true";

    private static final long MAX_SECRET_BYTES = 8192;
    private static final String DEFAULT_APP_LINK_LABEL = "Portfolio Performance";
    private static final int MAX_APP_LINK_LABEL = 60;

    public record Error(String setting, String message)
    {
        @Override
        public String toString()
        {
            return setting + ": " + message;
        }
    }

    /** The optional link back to the application shown in the page header. */
    public record AppLink(String url, String label)
    {
    }

    public record Settings(URI ppUrl, Optional<String> ppToken, Optional<String> ppFile,
                    Optional<String> reportingCurrency, Optional<String> eulerpoolKey, boolean localStorageConfirmed,
                    boolean registerLookup, long requestBudget, Duration runTimeout, Path cacheDir,
                    Optional<Path> outputDir, Optional<AppLink> appLink)
    {
        /** A key is set but the confirmation is not: provider data is not used (design D6). */
        public boolean unconfirmedKey()
        {
            return eulerpoolKey.isPresent() && !localStorageConfirmed;
        }

        /** Provider requests are allowed: key and confirmation. */
        public boolean providerEnabled()
        {
            return eulerpoolKey.isPresent() && localStorageConfirmed;
        }

        /** Non-secret settings that change the result, for the input fingerprint. */
        public String fingerprintPart()
        {
            return String.join("|", ppUrl.toString(), ppFile.orElse(""), reportingCurrency.orElse(""),
                            String.valueOf(eulerpoolKey.isPresent()), String.valueOf(localStorageConfirmed),
                            String.valueOf(registerLookup), String.valueOf(requestBudget),
                            appLink.map(link -> link.url() + "\n" + link.label()).orElse(""));
        }

        @Override
        public String toString()
        {
            return "Settings[ppUrl=" + ppUrl + ", ppFile=" + ppFile.orElse("") + ", key="
                            + (eulerpoolKey.isPresent() ? "set" : "unset") + ", localStorageConfirmed="
                            + localStorageConfirmed + "]";
        }
    }

    public record Result(Optional<Settings> settings, List<Error> errors)
    {
        public Result
        {
            errors = List.copyOf(errors);
        }
    }

    private final Map<String, String> values;
    private final List<Error> errors = new ArrayList<>();

    private LensConfig(Map<String, String> values)
    {
        this.values = values;
    }

    /**
     * Reads the configuration. {@code home} is used for the default cache directory.
     *
     * @param requireOutput
     *            whether the output directory is required (the run command writes a page)
     */
    public static Result load(Map<String, String> environment, Optional<Path> configFile, Path home,
                    boolean requireOutput)
    {
        var merged = new HashMap<String, String>();
        var errors = new ArrayList<Error>();
        if (configFile.isPresent())
        {
            var properties = new Properties();
            try (InputStream in = Files.newInputStream(configFile.get()))
            {
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                properties.stringPropertyNames().forEach(name -> merged.put(name, properties.getProperty(name)));
            }
            catch (IOException | IllegalArgumentException e)
            {
                errors.add(new Error("--config", "cannot read the configuration file"));
            }
        }
        environment.forEach((name, value) -> {
            if (name.startsWith("LENS_") || name.equals("XDG_CACHE_HOME"))
                merged.put(name, value);
        });
        var config = new LensConfig(merged);
        config.errors.addAll(errors);
        var settings = config.read(home, requireOutput);
        return new Result(config.errors.isEmpty() ? Optional.of(settings) : Optional.empty(), config.errors);
    }

    private Settings read(Path home, boolean requireOutput)
    {
        var url = text(PP_URL).orElse("http://127.0.0.1:5712");
        URI ppUrl = URI.create("http://127.0.0.1:5712");
        try
        {
            ppUrl = PpClient.validateBaseUrl(URI.create(url.trim()));
        }
        catch (IllegalArgumentException e)
        {
            errors.add(new Error(PP_URL, e.getMessage() == null ? "is not a valid URL" : e.getMessage()));
        }

        var token = secret(PP_TOKEN);
        if (token.isEmpty() && errors.stream().noneMatch(e -> e.setting().startsWith(PP_TOKEN)))
            errors.add(new Error(PP_TOKEN, "is required: create a token in Portfolio Performance (Preferences > MCP"
                            + " Server & REST API > Add client) and set " + PP_TOKEN + " or " + PP_TOKEN + FILE_SUFFIX));
        else if (token.isPresent() && !CompositionCredentials.isValid(token.get()))
            errors.add(new Error(PP_TOKEN, "contains characters that a token cannot contain (for example spaces)"));

        var key = secret(EULERPOOL_API_KEY);
        if (key.isPresent() && !CompositionCredentials.isValid(key.get()))
        {
            errors.add(new Error(EULERPOOL_API_KEY,
                            "is not a valid key: it contains whitespace, a line break or another character a key cannot contain"));
            key = Optional.empty();
        }

        var confirmed = bool(LOCAL_STORAGE_CONFIRMED, false);
        var register = bool(REGISTER_LOOKUP, true);

        long budget = 20000;
        var budgetText = text(REQUEST_BUDGET);
        if (budgetText.isPresent())
        {
            try
            {
                budget = Long.parseLong(budgetText.get().trim());
                if (budget < 0)
                    throw new NumberFormatException();
            }
            catch (NumberFormatException e)
            {
                errors.add(new Error(REQUEST_BUDGET, "must be a whole number of request units, 0 or more"));
            }
        }

        var timeout = Duration.ofMinutes(2);
        var timeoutText = text(RUN_TIMEOUT);
        if (timeoutText.isPresent())
        {
            try
            {
                timeout = Duration.parse(timeoutText.get().trim());
                if (timeout.isNegative() || timeout.isZero())
                    throw new DateTimeParseException("not positive", timeoutText.get(), 0);
            }
            catch (DateTimeParseException e)
            {
                errors.add(new Error(RUN_TIMEOUT, "must be a positive ISO-8601 duration such as PT2M"));
            }
        }

        var currency = text(REPORTING_CURRENCY).map(String::trim);
        if (currency.isPresent() && !currency.get().matches("[A-Z]{3}"))
            errors.add(new Error(REPORTING_CURRENCY, "must be a three-letter ISO 4217 code such as EUR"));

        var cacheDir = text(CACHE_DIR).map(Path::of).orElseGet(() -> text("XDG_CACHE_HOME").map(Path::of)
                        .orElse(home.resolve(".cache")).resolve("portfolio-lens"));
        var outputDir = text(OUTPUT_DIR).map(Path::of);
        if (requireOutput && outputDir.isEmpty())
            errors.add(new Error(OUTPUT_DIR, "is required: the directory where index.html is written"));

        var appLink = appLink();

        return new Settings(ppUrl, token, text(PP_FILE).map(String::trim), currency, key, confirmed, register, budget,
                        timeout, cacheDir, outputDir, appLink);
    }

    private Optional<AppLink> appLink()
    {
        var rawUrl = text(APP_LINK_URL);
        var rawLabel = Optional.ofNullable(values.get(APP_LINK_LABEL)).filter(value -> !value.isBlank());
        if (rawUrl.isEmpty())
        {
            if (rawLabel.isPresent())
                errors.add(new Error(APP_LINK_LABEL, "has no effect without " + APP_LINK_URL));
            return Optional.empty();
        }
        var label = rawLabel.map(String::trim).orElse(DEFAULT_APP_LINK_LABEL);
        var valid = true;
        if (label.length() > MAX_APP_LINK_LABEL || label.chars().anyMatch(Character::isISOControl))
        {
            errors.add(new Error(APP_LINK_LABEL,
                            "must be at most " + MAX_APP_LINK_LABEL + " characters without line breaks"));
            valid = false;
        }
        URI uri = null;
        try
        {
            uri = new URI(rawUrl.get().trim());
        }
        catch (java.net.URISyntaxException e)
        {
            // reported below
        }
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getRawUserInfo() != null
                        || uri.getScheme() == null
                        || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https")))
        {
            errors.add(new Error(APP_LINK_URL, "must be an absolute http or https URL without user name or password,"
                            + " for example https://example.org/"));
            return Optional.empty();
        }
        return valid ? Optional.of(new AppLink(uri.toString(), label)) : Optional.empty();
    }

    private Optional<String> text(String name)
    {
        return Optional.ofNullable(values.get(name)).filter(value -> !value.isBlank());
    }

    private boolean bool(String name, boolean fallback)
    {
        var value = Optional.ofNullable(values.get(name));
        if (value.isEmpty() || value.get().isEmpty())
            return fallback;
        return switch (value.get())
        {
            case "true" -> true;
            case "false" -> false;
            default -> {
                errors.add(new Error(name, "must be true or false"));
                yield fallback;
            }
        };
    }

    /** A secret from {@code NAME} or the file named by {@code NAME_FILE}, never both. */
    private Optional<String> secret(String name)
    {
        var direct = text(name);
        var file = text(name + FILE_SUFFIX);
        if (direct.isPresent() && file.isPresent())
        {
            errors.add(new Error(name, "is set both directly and through " + name + FILE_SUFFIX + "; set only one"));
            return Optional.empty();
        }
        if (direct.isPresent())
            return direct;
        if (file.isEmpty())
            return Optional.empty();
        var path = Path.of(file.get());
        try
        {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_SECRET_BYTES)
                throw new IOException("not a small regular file");
            var content = Files.readString(path, StandardCharsets.UTF_8);
            // one trailing line break, as editors and secret mounts add it
            if (content.endsWith("\r\n"))
                content = content.substring(0, content.length() - 2);
            else if (content.endsWith("\n"))
                content = content.substring(0, content.length() - 1);
            return content.isEmpty() ? Optional.empty() : Optional.of(content);
        }
        catch (IOException | RuntimeException e)
        {
            errors.add(new Error(name + FILE_SUFFIX, "names a file that cannot be read"));
            return Optional.empty();
        }
    }
}
