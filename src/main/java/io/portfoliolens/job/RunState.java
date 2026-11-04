package io.portfoliolens.job;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.portfoliolens.core.CompositionRefreshCoordinator.Attempt;
import io.portfoliolens.core.CompositionRequestBudget;
import io.portfoliolens.core.CompositionRequestBudget.AccountSnapshot;
import io.portfoliolens.core.CompositionRequestBudget.Charge;
import io.portfoliolens.core.ProviderAccount;

/**
 * What one run hands to the next (design D7): provider budget charges, backoff, suspension,
 * per-fund attempt times and the input fingerprint. It holds instrument identifiers of held funds
 * but no provider payload, no amounts and no credentials; the key revision is a salted HMAC of
 * the key with a random salt kept in this file, so it changes when the key changes.
 */
public final class RunState
{
    public static final String FILE = "state.json";
    private static final int VERSION = 1;

    private CompositionRequestBudget.Snapshot budget = new CompositionRequestBudget.Snapshot(List.of(), Instant.MIN,
                    Map.of());
    private List<Attempt> attempts = List.of();
    private Optional<String> fingerprint = Optional.empty();
    private Optional<Instant> nextDueAt = Optional.empty();
    private Map<Map<String, String>, LookThroughSession.LookupBackoff> lookupBackoff = new HashMap<>();
    private byte[] salt;

    private RunState(byte[] salt)
    {
        this.salt = salt;
    }

    public static RunState empty()
    {
        var salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return new RunState(salt);
    }

    /** Reads the state; empty when the file is missing. Throws when it exists but cannot be read. */
    public static Optional<RunState> read(Path directory, ProviderAccount account) throws IOException
    {
        var file = directory.resolve(FILE);
        if (!Files.exists(file))
            return Optional.empty();
        try
        {
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("version").getAsInt() != VERSION)
                throw new IOException("unsupported state version");
            var state = new RunState(HexFormat.of().parseHex(json.get("salt").getAsString()));
            var budget = json.getAsJsonObject("budget");
            var charges = new ArrayList<Charge>();
            for (var charge : budget.getAsJsonArray("charges"))
            {
                var object = charge.getAsJsonObject();
                charges.add(new Charge(Instant.parse(object.get("at").getAsString()), object.get("units").getAsLong()));
            }
            var accounts = new HashMap<ProviderAccount, AccountSnapshot>();
            if (budget.has("account"))
            {
                var object = budget.getAsJsonObject("account");
                accounts.put(account, new AccountSnapshot(object.get("configuration").getAsLong(),
                                object.get("suspended").getAsBoolean(), instant(object.get("retryAt").getAsString())));
            }
            state.budget = new CompositionRequestBudget.Snapshot(charges,
                            instant(budget.get("providerRetryAt").getAsString()), accounts);
            var attempts = new ArrayList<Attempt>();
            for (var attempt : json.getAsJsonArray("attempts"))
            {
                var object = attempt.getAsJsonObject();
                var identifiers = new TreeMap<String, String>();
                object.getAsJsonObject("identifiers").entrySet()
                                .forEach(entry -> identifiers.put(entry.getKey(), entry.getValue().getAsString()));
                attempts.add(new Attempt(identifiers, Instant.parse(object.get("attemptedAt").getAsString()),
                                instant(object.get("retryAt").getAsString()), object.get("noComposition").getAsBoolean()));
            }
            state.attempts = List.copyOf(attempts);
            state.fingerprint = json.has("fingerprint") ? Optional.of(json.get("fingerprint").getAsString())
                            : Optional.empty();
            if (json.has("lookupBackoff"))
                for (var item : json.getAsJsonArray("lookupBackoff"))
                {
                    var object = item.getAsJsonObject();
                    var identifiers = new TreeMap<String, String>();
                    object.getAsJsonObject("identifiers").entrySet()
                                    .forEach(entry -> identifiers.put(entry.getKey(), entry.getValue().getAsString()));
                    state.lookupBackoff.put(Map.copyOf(identifiers), new LookThroughSession.LookupBackoff(
                                    object.get("failures").getAsInt(), Instant.parse(object.get("nextAttempt").getAsString())));
                }
            state.nextDueAt = json.has("nextDueAt") ? Optional.of(Instant.parse(json.get("nextDueAt").getAsString()))
                            : Optional.empty();
            return Optional.of(state);
        }
        catch (RuntimeException e)
        {
            throw new IOException("unreadable state file", e);
        }
    }

    public void write(Path directory, ProviderAccount account) throws IOException
    {
        var json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("salt", HexFormat.of().formatHex(salt));
        var budgetJson = new JsonObject();
        var charges = new JsonArray();
        for (var charge : budget.charges())
        {
            var object = new JsonObject();
            object.addProperty("at", charge.at().toString());
            object.addProperty("units", charge.units());
            charges.add(object);
        }
        budgetJson.add("charges", charges);
        budgetJson.addProperty("providerRetryAt", text(budget.providerRetryAt()));
        var accountState = budget.accounts().get(account);
        if (accountState != null)
        {
            var object = new JsonObject();
            object.addProperty("configuration", accountState.configuration());
            object.addProperty("suspended", accountState.suspended());
            object.addProperty("retryAt", text(accountState.retryAt()));
            budgetJson.add("account", object);
        }
        json.add("budget", budgetJson);
        var attemptsJson = new JsonArray();
        for (var attempt : attempts)
        {
            var object = new JsonObject();
            var identifiers = new JsonObject();
            new TreeMap<>(attempt.identifiers()).forEach(identifiers::addProperty);
            object.add("identifiers", identifiers);
            object.addProperty("attemptedAt", attempt.attemptedAt().toString());
            object.addProperty("retryAt", text(attempt.retryAt()));
            object.addProperty("noComposition", attempt.noComposition());
            attemptsJson.add(object);
        }
        json.add("attempts", attemptsJson);
        fingerprint.ifPresent(value -> json.addProperty("fingerprint", value));
        nextDueAt.ifPresent(value -> json.addProperty("nextDueAt", value.toString()));
        var backoffJson = new JsonArray();
        lookupBackoff.forEach((identifiers, value) -> {
            var object = new JsonObject();
            var ids = new JsonObject();
            new TreeMap<>(identifiers).forEach(ids::addProperty);
            object.add("identifiers", ids);
            object.addProperty("failures", value.failures());
            object.addProperty("nextAttempt", value.nextAttempt().toString());
            backoffJson.add(object);
        });
        json.add("lookupBackoff", backoffJson);
        Files.createDirectories(directory);
        AtomicFiles.write(directory.resolve(FILE),
                        new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    private static String text(Instant instant)
    {
        return instant.equals(Instant.MIN) ? "MIN" : instant.toString();
    }

    private static Instant instant(String text)
    {
        return "MIN".equals(text) ? Instant.MIN : Instant.parse(text);
    }

    public CompositionRequestBudget.Snapshot budget()
    {
        return budget;
    }

    public void setBudget(CompositionRequestBudget.Snapshot budget)
    {
        this.budget = budget;
    }

    public List<Attempt> attempts()
    {
        return attempts;
    }

    public void setAttempts(List<Attempt> attempts)
    {
        this.attempts = List.copyOf(attempts);
    }

    public Optional<String> fingerprint()
    {
        return fingerprint;
    }

    public void setFingerprint(Optional<String> fingerprint)
    {
        this.fingerprint = fingerprint;
    }

    /** Classification lookups on hold after failures, by identifiers; modifiable. */
    public Map<Map<String, String>, LookThroughSession.LookupBackoff> lookupBackoff()
    {
        return lookupBackoff;
    }

    /** When the next run has provider work even if its inputs are unchanged. */
    public Optional<Instant> nextDueAt()
    {
        return nextDueAt;
    }

    public void setNextDueAt(Optional<Instant> nextDueAt)
    {
        this.nextDueAt = nextDueAt;
    }

    /** A revision of the key that changes with the key and reveals nothing about it. */
    public long keyRevision(Optional<String> key)
    {
        if (key.isEmpty())
            return 0;
        try
        {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            var digest = mac.doFinal(key.get().getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++)
                value = (value << 8) | (digest[i] & 0xff);
            return value == 0 ? 1 : value;
        }
        catch (java.security.GeneralSecurityException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
