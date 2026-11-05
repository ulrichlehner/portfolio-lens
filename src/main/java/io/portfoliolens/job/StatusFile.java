package io.portfoliolens.job;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.portfoliolens.page.PageInput.ProviderState;

/**
 * {@code status.json} beside the page, for monitoring: when the page was generated, when the job
 * last checked, the last failure and whether provider data is used. It holds no amounts, names,
 * paths or secrets.
 */
public final class StatusFile
{
    public static final String FILE = "status.json";

    private final JsonObject json;

    private StatusFile(JsonObject json)
    {
        this.json = json;
    }

    /** The current status, or an empty one when the file is missing or unreadable. */
    public static StatusFile read(Path outputDir)
    {
        try
        {
            var file = outputDir.resolve(FILE);
            if (Files.exists(file))
                return new StatusFile(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        }
        catch (IOException | RuntimeException e)
        {
            // a broken status file is replaced
        }
        return new StatusFile(new JsonObject());
    }

    private void common(Instant checkedAt, ProviderState provider)
    {
        json.addProperty("lastCheckedAt", checkedAt.toString());
        json.addProperty("providerData", provider.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (provider == ProviderState.UNCONFIRMED)
            json.addProperty("warning", LensRun.unconfirmedWarning());
        else
            json.remove("warning");
    }

    public void checked(Instant at, ProviderState provider)
    {
        common(at, provider);
        json.addProperty("result", "skipped");
        json.remove("error");
    }

    public void written(Instant startedAt, Instant generatedAt, ProviderState provider, int pendingLookups,
                    Optional<Instant> nextAttempt)
    {
        common(startedAt, provider);
        json.addProperty("result", "written");
        json.addProperty("generatedAt", generatedAt.toString());
        json.addProperty("lastSuccessAt", generatedAt.toString());
        json.addProperty("pendingLookups", pendingLookups);
        if (nextAttempt.isPresent())
            json.addProperty("nextAttemptAt", nextAttempt.get().toString());
        else
            json.remove("nextAttemptAt");
        json.remove("error");
    }

    public void budget(io.portfoliolens.core.CompositionRequestBudget.Usage usage)
    {
        var budget = new JsonObject();
        budget.addProperty("usedUnits", usage.used());
        budget.addProperty("capacityUnits", usage.capacity());
        budget.addProperty("window", usage.window().toString());
        budget.addProperty("exhausted", usage.exhausted());
        usage.oldestExpiresAt().ifPresent(at -> budget.addProperty("nextUnitsFreeAt", at.toString()));
        json.add("budget", budget);
    }

    public void failed(Instant at, String message, ProviderState provider)
    {
        common(at, provider);
        json.addProperty("result", "failed");
        json.addProperty("error", message);
        json.addProperty("lastFailureAt", at.toString());
    }

    public Optional<String> get(String name)
    {
        return json.has(name) ? Optional.of(json.get(name).getAsString()) : Optional.empty();
    }

    public void write(Path outputDir) throws IOException
    {
        AtomicFiles.write(outputDir.resolve(FILE),
                        new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8));
    }
}
