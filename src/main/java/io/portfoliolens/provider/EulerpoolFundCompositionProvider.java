package io.portfoliolens.provider;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;


import io.portfoliolens.Version;
import io.portfoliolens.http.HttpGet;
import io.portfoliolens.http.JdkHttpGet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import io.portfoliolens.core.CompanyAttributes;
import io.portfoliolens.core.CompanyEnrichmentCache;
import io.portfoliolens.core.CompanyProfile;
import io.portfoliolens.core.CompositionInstrument;
import io.portfoliolens.core.CompositionPersistencePolicy;
import io.portfoliolens.core.CompositionPersistencePolicy.Permission;
import io.portfoliolens.core.CompositionProvenance;
import io.portfoliolens.core.CompositionRefreshCoordinator.EnrichmentFailure;
import io.portfoliolens.core.CompositionResult;
import io.portfoliolens.core.CompositionResult.Status;
import io.portfoliolens.core.FundComposition;
import io.portfoliolens.core.FundComposition.Completeness;
import io.portfoliolens.core.FundComposition.Constituent;
import io.portfoliolens.core.FundComposition.Denominator;
import io.portfoliolens.core.FundComposition.ExposureBasis;
import io.portfoliolens.core.InstrumentClassifier;
import io.portfoliolens.core.InstrumentEvidence;
import io.portfoliolens.core.InstrumentEvidence.Assertion;
import io.portfoliolens.core.InstrumentEvidence.AssetType;
import io.portfoliolens.core.InstrumentEvidence.Identifier;
import io.portfoliolens.core.InstrumentEvidence.Provenance;
import io.portfoliolens.core.ProviderAccount;
import io.portfoliolens.core.RevenueSplit;

/**
 * Source adapter for the public Eulerpool OpenAPI holdings schema:
 * https://api.eulerpool.com/api/1/documentation/yaml. The endpoint describes top
 * holdings, not validated NAV or economic exposure. No classification or company
 * identity is inferred from labels, ISINs or nullable assetType strings.
 *
 * Lookup prefers the ISIN namespace, then eulerpool:symbol. Credentials are
 * resolved per call/account, never configured on a shared discovered instance.
 * The no-argument discovery instance is deliberately unconfigured; local secure
 * storage/session entry integration must supply CredentialAccess separately.
 */
@SuppressWarnings("nls")
public final class EulerpoolFundCompositionProvider implements FundCompositionProvider, InstrumentClassifier
{
    public static final String ID = "EULERPOOL";
    public static final String SYMBOL = "eulerpool:symbol";
    /**
     * Worst-case requests of one classification: profile, search and bond
     * list. Most instruments need one; callers release the unused part.
     */
    public static final int CLASSIFICATION_REQUEST_COST = 3;

    /** Longest descriptive profile value kept; the company cache stores at most 1024 characters. */
    static final int MAX_PROFILE_TEXT = 256;

    @FunctionalInterface
    public interface CredentialAccess
    {
        Optional<String> getBearerToken(ProviderAccount account) throws IOException;
    }

    /** Transport implementations must not log credentials or follow redirects. */
    @FunctionalInterface
    public interface Transport
    {
        Response get(URI uri, String authorization) throws IOException;
    }

    public record Response(int status, String body, Optional<String> retryAfter)
    {
        public Response
        {
            Objects.requireNonNull(retryAfter);
        }

        @Override
        public String toString()
        {
            return "Eulerpool response [status=" + status + "]";
        }
    }

    private final CredentialAccess credentials;
    private final Transport transport;
    private final Clock clock;
    private final Predicate<ProviderAccount> localStorageConfirmed;

    public EulerpoolFundCompositionProvider()
    {
        this(account -> Optional.empty());
    }

    public EulerpoolFundCompositionProvider(CredentialAccess credentials)
    {
        this(credentials, EulerpoolFundCompositionProvider::download, Clock.systemUTC());
    }

    public EulerpoolFundCompositionProvider(CredentialAccess credentials,
                    Predicate<ProviderAccount> localStorageConfirmed)
    {
        this(credentials, EulerpoolFundCompositionProvider::download, Clock.systemUTC(), localStorageConfirmed);
    }

    public EulerpoolFundCompositionProvider(CredentialAccess credentials, Transport transport, Clock clock)
    {
        this(credentials, transport, clock, account -> false);
    }

    /**
     * @param localStorageConfirmed
     *            the user's per-account confirmation that the plan allows local
     *            storage; without it the persistence permission stays unverified
     */
    public EulerpoolFundCompositionProvider(CredentialAccess credentials, Transport transport, Clock clock,
                    Predicate<ProviderAccount> localStorageConfirmed)
    {
        this.localStorageConfirmed = Objects.requireNonNull(localStorageConfirmed);
        this.credentials = Objects.requireNonNull(credentials);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public CompositionPersistencePolicy getPersistencePolicy(ProviderAccount account)
    {
        checkAccount(account);
        // The user's own confirmation; no maximum age is introduced.
        return new CompositionPersistencePolicy(account,
                        localStorageConfirmed.test(account) ? Permission.PERMITTED : Permission.UNVERIFIED,
                        Optional.empty());
    }

    @Override
    public CompositionResult getComposition(ProviderAccount account, CompositionInstrument fund)
    {
        checkAccount(account);
        Objects.requireNonNull(fund);
        var identifier = Optional.ofNullable(fund.identifiers().get("ISIN")).filter(s -> !s.isBlank())
                        .or(() -> Optional.ofNullable(fund.identifiers().get(SYMBOL)).filter(s -> !s.isBlank()));
        // Restrict identifiers to one path segment, never a URL or query string.
        if (identifier.isEmpty() || !identifier.get().matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
            return failure(Status.UNSUPPORTED);

        try
        {
            var token = credentials.getBearerToken(account);
            if (token.isEmpty() || !token.get().matches("[A-Za-z0-9._~+/-]+=*"))
                return failure(Status.AUTHENTICATION_FAILED);

            var uri = URI.create("https://api.eulerpool.com/api/1/etf/holdings/" + identifier.get());
            var response = transport.get(uri, "Bearer " + token.get());
            return switch (response.status())
            {
                case 200 -> parse(account, fund, response.body());
                case 204, 404 -> failure(Status.UNAVAILABLE);
                case 401 -> failure(Status.AUTHENTICATION_FAILED);
                case 402, 403 -> failure(Status.ENTITLEMENT_FAILED);
                case 429 -> new CompositionResult(Status.RATE_LIMITED, Optional.empty(),
                                retryAfter(response.retryAfter()));
                default -> failure(Status.FAILED);
            };
        }
        catch (InterruptedIOException e)
        {
            return failure(Thread.currentThread().isInterrupted() ? Status.CANCELLED : Status.FAILED);
        }
        catch (IOException | RuntimeException e)
        {
            // No response bodies, exception messages, headers or keys escape.
            return failure(Status.FAILED);
        }
    }

    @Override
    public Optional<InstrumentEvidence> classify(ProviderAccount account, CompositionInstrument instrument)
                    throws IOException
    {
        return classify(account, instrument, null, false, new AtomicInteger());
    }

    /**
     * Classification that shares company profiles with enrichment. Every
     * equity/profile answer, also "not found", goes into the profile cache, so
     * enrichment does not request it again, and a cached answer replaces the
     * profile request. With profileFirst, an ISIN is classified from its
     * profile first: a profile with a sector or industry establishes an
     * equity in one request. Search and the bond list follow only when the
     * profile does not decide. Profile and bond list are asked by ISIN only,
     * since their answers can only match an ISIN.
     *
     * @param profiles
     *            the shared profile cache, or null for none
     * @param requests
     *            counts the provider requests made, so a caller can release
     *            the unused part of its budget reservation
     */
    public Optional<InstrumentEvidence> classify(ProviderAccount account, CompositionInstrument instrument,
                    CompanyEnrichmentCache profiles, boolean profileFirst, AtomicInteger requests) throws IOException
    {
        checkAccount(account);
        Objects.requireNonNull(instrument);
        Objects.requireNonNull(requests);
        var queries = identifiers(instrument);
        if (queries.isEmpty())
            return Optional.empty();
        var token = credentials.getBearerToken(account);
        if (token.isEmpty() || !token.get().matches("[A-Za-z0-9._~+/-]+=*"))
            throw new EnrichmentFailure(failure(Status.AUTHENTICATION_FAILED));
        var isin = Optional.ofNullable(instrument.identifiers().get("ISIN")).map(String::trim)
                        .filter(queries::contains);

        if (profileFirst && isin.isPresent())
        {
            var evidence = profileEvidence(account, instrument, isin.get(), token.get(), profiles, requests);
            if (evidence.isPresent())
                return evidence;
        }
        // Exact ISIN or ticker evidence only. A result's ISIN is never used to
        // replace the source identity: some providers index a different listing.
        for (var query : queries)
        {
            var search = request("https://api.eulerpool.com/api/1/equity/search?q=" + query, token.get(), requests);
            if (search.status() == 200)
            {
                var evidence = parseSearch(account, instrument, search.body());
                if (evidence.isPresent())
                    return evidence;
            }
            else if (!answeredWithout(search.status()))
                throw new IOException();
        }
        if (isin.isEmpty())
            return Optional.empty();
        if (!profileFirst)
        {
            var evidence = profileEvidence(account, instrument, isin.get(), token.get(), profiles, requests);
            if (evidence.isPresent())
                return evidence;
        }
        var bonds = request("https://api.eulerpool.com/api/1/bonds/list?q=" + isin.get(), token.get(), requests);
        if (bonds.status() == 200 && isBond(isin.get(), bonds.body()))
            return bondEvidence(account, instrument);
        if (bonds.status() != 200 && !answeredWithout(bonds.status()))
            throw new IOException();
        return Optional.empty();
    }

    /**
     * A status that answers for this instrument alone that the provider has no
     * data for it: no content, not found or another client error that is not
     * an account, rate or timeout problem (those are raised before). Server
     * errors and unexpected statuses are transient and not an answer.
     */
    private static boolean answeredWithout(int status)
    {
        return status == 204 || status >= 400 && status < 500 && status != 408;
    }

    /**
     * Equity evidence from the ISIN's profile, from the shared cache when it
     * holds an answer, otherwise from one request whose answer is shared.
     */
    private Optional<InstrumentEvidence> profileEvidence(ProviderAccount account, CompositionInstrument instrument,
                    String isin, String token, CompanyEnrichmentCache profiles, AtomicInteger requests)
                    throws IOException
    {
        // Only the ISIN form that enrichment requests shares its cache entry.
        var shared = profiles != null && isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]") ? profiles : null;
        var cached = shared == null ? Optional.<CompanyEnrichmentCache.Cached<CompanyProfile>>empty()
                        : shared.profile(account, isin);
        if (cached.isPresent())
            return cached.get().value().filter(value -> value.sector().isPresent() || value.industry().isPresent())
                            .flatMap(value -> buildEvidence(account, instrument, "stock", "equity/profile"));
        // Captured before the request, so an answer arriving after a
        // withdrawal of local storage is kept for the session only.
        var generation = shared == null ? 0 : shared.generation(account);
        var response = request("https://api.eulerpool.com/api/1/equity/profile/" + isin, token, requests);
        if (response.status() == 204 || response.status() == 404)
        {
            if (shared != null)
                shared.putProfile(account, isin, Optional.empty(), generation);
            return Optional.empty();
        }
        // Another client error is no profile for this instrument, but not a
        // shared answer for enrichment.
        if (answeredWithout(response.status()))
            return Optional.empty();
        if (response.status() != 200)
            throw new IOException();
        if (shared != null)
        {
            try
            {
                shared.putProfile(account, isin, Optional.of(parseCompanyProfile(isin, response.body())),
                                generation);
            }
            catch (IOException e)
            {
                // A malformed profile is not shared; enrichment asks for it itself.
            }
        }
        return parseProfile(account, instrument, response.body());
    }

    private Response request(String url, String token, AtomicInteger requests) throws IOException
    {
        requests.incrementAndGet();
        return classificationResponse(url, token);
    }

    /**
     * Descriptive company profile of an ISIN from equity/profile: country,
     * currency, sector, industry and market capitalization (in millions).
     * Empty when the provider has no profile. A malformed answer, or one for
     * another ISIN, fails instead of counting as "no profile".
     */
    public Optional<CompanyProfile> profile(ProviderAccount account, String isin) throws IOException
    {
        checkAccount(account);
        if (isin == null || !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]"))
            throw new IllegalArgumentException();
        var token = credentials.getBearerToken(account);
        if (token.isEmpty() || !token.get().matches("[A-Za-z0-9._~+/-]+=*"))
            throw new EnrichmentFailure(failure(Status.AUTHENTICATION_FAILED));
        var response = classificationResponse("https://api.eulerpool.com/api/1/equity/profile/" + isin, token.get());
        if (response.status() == 204 || response.status() == 404)
            return Optional.empty();
        if (response.status() != 200)
            throw new IOException();
        return Optional.of(parseCompanyProfile(isin, response.body()));
    }

    private CompanyProfile parseCompanyProfile(String isin, String body) throws IOException
    {
        if (body == null || body.isBlank())
            throw new IOException();
        try (var reader = new JsonReader(new StringReader(body)))
        {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException();
            var row = json.getAsJsonObject();
            if (!isin.equalsIgnoreCase(text(row, "isin").orElse("")))
                throw new IOException();
            // Country names that do not map to an ISO code stay unknown. A descriptive field of an
            // unexpected type is unknown too: it must not make the whole answer fail, which
            // would ask for the same answer again in every run.
            var country = optionalText(row, "country").flatMap(CompanyAttributes::countryOfName);
            var currency = optionalText(row, "currency").map(String::trim).filter(code -> code.matches("[A-Z]{3}"));
            // Overlong values stay unknown as well, so that the cache can always store the profile.
            var sector = optionalText(row, "sector").map(String::trim)
                            .filter(value -> !value.isEmpty() && value.length() <= MAX_PROFILE_TEXT);
            var industry = optionalText(row, "industry").map(String::trim)
                            .filter(value -> !value.isEmpty() && value.length() <= MAX_PROFILE_TEXT);
            var marketCap = optionalNumber(row, "mcap").filter(value -> value.signum() >= 0)
                            .filter(value -> value.toPlainString().length() <= MAX_PROFILE_TEXT);
            return new CompanyProfile(isin, country, currency, sector, industry, marketCap, clock.instant());
        }
        catch (RuntimeException e)
        {
            // No response bodies or exception messages escape.
            throw new IOException();
        }
    }

    /**
     * Latest reported revenue by geography of an ISIN from equity/regions,
     * normalized and reduced to percentages. Only entries of the newest
     * period on a geographical axis count; if several geographical axes
     * exist for it, the one with the most entries. Empty when the provider
     * has none. A malformed answer fails instead of counting as "no data".
     */
    public Optional<RevenueSplit> revenueRegions(ProviderAccount account, String isin) throws IOException
    {
        checkAccount(account);
        if (isin == null || !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]"))
            throw new IllegalArgumentException();
        var token = credentials.getBearerToken(account);
        if (token.isEmpty() || !token.get().matches("[A-Za-z0-9._~+/-]+=*"))
            throw new EnrichmentFailure(failure(Status.AUTHENTICATION_FAILED));
        var response = classificationResponse("https://api.eulerpool.com/api/1/equity/regions/" + isin, token.get());
        if (response.status() == 204 || response.status() == 404)
            return Optional.empty();
        if (response.status() != 200)
            throw new IOException();
        return parseRevenueRegions(isin, response.body());
    }

    private Optional<RevenueSplit> parseRevenueRegions(String isin, String body) throws IOException
    {
        if (body == null || body.isBlank())
            throw new IOException();
        try (var reader = new JsonReader(new StringReader(body)))
        {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonArray() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException();
            var periods = new TreeMap<LocalDate, Map<String, List<RevenueSplit.Entry>>>();
            for (var element : json.getAsJsonArray())
            {
                if (!element.isJsonObject())
                    throw new IOException();
                var row = element.getAsJsonObject();
                var period = LocalDate.parse(text(row, "period").orElseThrow());
                var axis = text(row, "axis").orElse("");
                var percentage = number(row, "percentage");
                var entry = new RevenueSplit.Entry(text(row, "label").orElse(""), text(row, "member").orElse(""),
                                percentage.orElse(null));
                if (axis.toLowerCase(Locale.ROOT).contains("geograph"))
                    periods.computeIfAbsent(period, ignored -> new TreeMap<>())
                                    .computeIfAbsent(axis, ignored -> new ArrayList<>()).add(entry);
            }
            // The newest period that has usable entries on a geographical axis.
            for (var period : periods.descendingKeySet())
            {
                var axis = periods.get(period).entrySet().stream()
                                .max(Comparator.comparingInt((Map.Entry<String, List<RevenueSplit.Entry>> item) -> item
                                                .getValue().size()))
                                .orElseThrow();
                var split = RevenueSplit.of(isin, period, axis.getValue(), clock.instant());
                if (split.isPresent())
                    return split;
            }
            return Optional.empty();
        }
        catch (RuntimeException e)
        {
            // No response bodies or exception messages escape.
            throw new IOException();
        }
    }

    private List<String> identifiers(CompositionInstrument instrument)
    {
        var result = new ArrayList<String>();
        var isin = instrument.identifiers().get("ISIN");
        if (isin != null && !isin.isBlank())
            result.add(isin.trim());
        var symbol = instrument.identifiers().get(SYMBOL);
        if (result.isEmpty() && symbol != null && !symbol.isBlank())
            result.add(symbol.trim());
        result.removeIf(value -> !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"));
        return List.copyOf(result);
    }

    private Response classificationResponse(String url, String token) throws IOException
    {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedIOException();
        var response = transport.get(URI.create(url), "Bearer " + token);
        var status = switch (response.status())
        {
            case 401 -> Status.AUTHENTICATION_FAILED;
            case 402, 403 -> Status.ENTITLEMENT_FAILED;
            case 429 -> Status.RATE_LIMITED;
            default -> null;
        };
        if (status != null)
            throw new EnrichmentFailure(new CompositionResult(status, Optional.empty(),
                            status == Status.RATE_LIMITED ? retryAfter(response.retryAfter()) : Optional.empty()));
        return response;
    }

    private Optional<InstrumentEvidence> parseSearch(ProviderAccount account, CompositionInstrument instrument,
                    String body)
    {
        if (body == null || body.isBlank())
            return Optional.empty();
        try (var reader = new JsonReader(new StringReader(body)))
        {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                return Optional.empty();
            var results = json.getAsJsonObject().get("results");
            if (results == null || !results.isJsonArray())
                return Optional.empty();
            var isin = instrument.identifiers().get("ISIN");
            var symbol = instrument.identifiers().get(SYMBOL);
            Optional<InstrumentEvidence> match = Optional.empty();
            for (var element : results.getAsJsonArray())
            {
                if (!element.isJsonObject())
                    continue;
                var row = element.getAsJsonObject();
                var resultIsin = text(row, "isin").orElse(null);
                var resultTicker = text(row, "ticker").orElse(null);
                var type = text(row, "type").orElse(null);
                if (type == null)
                    continue;
                boolean exact = isin != null && !isin.isBlank() ? isin.equalsIgnoreCase(resultIsin)
                                : symbol != null && symbol.equalsIgnoreCase(resultTicker);
                if (exact)
                {
                    var candidate = buildEvidence(account, instrument, type, "equity/search");
                    if (match.isPresent() && !match.get().classification().map(Assertion::value)
                                    .equals(candidate.flatMap(InstrumentEvidence::classification).map(Assertion::value)))
                        return Optional.empty();
                    match = candidate;
                }
            }
            return match;
        }
        catch (IOException | RuntimeException e)
        {
            return Optional.empty();
        }
    }

    private Optional<InstrumentEvidence> parseProfile(ProviderAccount account, CompositionInstrument instrument,
                    String body)
    {
        if (body == null || body.isBlank())
            return Optional.empty();
        try (var reader = new JsonReader(new StringReader(body)))
        {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                return Optional.empty();
            var sector = optionalText(json.getAsJsonObject(), "sector").orElse("");
            var industry = optionalText(json.getAsJsonObject(), "industry").orElse("");
            var isin = instrument.identifiers().get("ISIN");
            if (isin == null || !isin.equalsIgnoreCase(text(json.getAsJsonObject(), "isin").orElse("")))
                return Optional.empty();
            if (sector.isBlank() && industry.isBlank())
                return Optional.empty();
            return buildEvidence(account, instrument, "stock", "equity/profile");
        }
        catch (IOException | RuntimeException e)
        {
            return Optional.empty();
        }
    }

    private Optional<InstrumentEvidence> buildEvidence(ProviderAccount account, CompositionInstrument instrument,
                    String type, String reference)
    {
        var assetType = switch (type.toLowerCase())
        {
            case "stock", "share", "equity" -> AssetType.EQUITY;
            case "etf", "fund", "mutual fund", "mutual_fund" -> AssetType.FUND;
            case "bond", "fixed income", "crypto", "cryptocurrency" -> AssetType.OTHER_NON_EQUITY;
            default -> null;
        };
        if (assetType == null)
            return Optional.empty();
        var provenance = new Provenance(account, reference, clock.instant());
        var isin = instrument.identifiers().get("ISIN");
        var identity = isin != null && !isin.isBlank() ? new Identifier("ISIN", isin.trim())
                        : new Identifier(SYMBOL, instrument.identifiers().get(SYMBOL));
        return Optional.of(new InstrumentEvidence(Map.of(identity.namespace(), identity.value()),
                        new Assertion<>(identity, provenance),
                        Optional.of(new Assertion<>(assetType, provenance)),
                        assetType == AssetType.EQUITY ? Optional.of(new Assertion<>(identity, provenance))
                                        : Optional.empty()));
    }

    private Optional<InstrumentEvidence> bondEvidence(ProviderAccount account, CompositionInstrument instrument)
    {
        var provenance = new Provenance(account, "bonds/list", clock.instant());
        var isin = instrument.identifiers().get("ISIN");
        var identity = isin != null && !isin.isBlank() ? new Identifier("ISIN", isin.trim())
                        : new Identifier(SYMBOL, instrument.identifiers().get(SYMBOL));
        return Optional.of(new InstrumentEvidence(Map.of(identity.namespace(), identity.value()),
                        new Assertion<>(identity, provenance),
                        Optional.of(new Assertion<>(AssetType.OTHER_NON_EQUITY, provenance)), Optional.empty()));
    }

    private boolean isBond(String query, String body)
    {
        if (body == null || body.isBlank())
            return false;
        try (var reader = new JsonReader(new StringReader(body)))
        {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                return false;
            var data = json.getAsJsonObject().get("data");
            if (data == null || !data.isJsonArray())
                return false;
            for (var element : data.getAsJsonArray())
            {
                if (element.isJsonObject() && query.equalsIgnoreCase(text(element.getAsJsonObject(), "isin").orElse("")))
                    return true;
            }
            return false;
        }
        catch (IOException | RuntimeException e)
        {
            return false;
        }
    }

    private CompositionResult parse(ProviderAccount account, CompositionInstrument fund, String body)
    {
        if (body == null || body.isBlank())
            return failure(Status.MALFORMED_RESPONSE);
        try (var reader = new JsonReader(new StringReader(body)))
        {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonArray() || reader.peek() != JsonToken.END_DOCUMENT)
                return failure(Status.MALFORMED_RESPONSE);
            var rows = json.getAsJsonArray();
            if (rows.isEmpty())
                return failure(Status.UNAVAILABLE);

            var constituents = new ArrayList<Constituent>();
            var refreshTimes = new ArrayList<Optional<Instant>>();
            // A provider answer that repeats one security with the same nominal
            // amount but a different weight is internally inconsistent. Observed
            // in a bond fund whose response merged two vintages or share classes:
            // identical ISIN and share, different percent and value. Such rows
            // must not be summed as if they were two legitimate positions.
            var nominalByInstrument = new HashMap<String, BigDecimal>();
            var weightByInstrument = new HashMap<String, BigDecimal>();
            for (var element : rows)
            {
                var row = element.getAsJsonObject();
                var ids = new HashMap<String, String>();
                for (var field : new String[] { "isin", "cusip", "symbol" })
                {
                    var value = text(row, field);
                    if (value.isPresent() && !value.get().isBlank())
                        ids.put(switch (field)
                        {
                            case "isin" -> "ISIN";
                            case "cusip" -> "CUSIP";
                            default -> SYMBOL;
                        }, value.get());
                }
                // Validate known nullable fields without inventing classifications.
                text(row, "assetType");
                text(row, "etf_symbol");
                text(row, "logo");
                var nominal = number(row, "share");
                number(row, "value");
                var percent = number(row, "percent");
                if (ids.containsKey("ISIN") && nominal.isPresent() && percent.isPresent()
                                && inconsistentDuplicate(ids.get("ISIN"), nominal.get(), percent.get(),
                                                nominalByInstrument, weightByInstrument))
                    return failure(Status.MALFORMED_RESPONSE);
                var weight = percent.map(value -> value.movePointLeft(2).toPlainString());
                constituents.add(new Constituent(new CompositionInstrument(text(row, "name").orElse(""), ids),
                                weight));
                refreshTimes.add(text(row, "updated_at").map(Instant::parse));
            }
            // A common refresh is representable; different/missing row vintages
            // must not be collapsed into the newest date or an invented date.
            var refresh = refreshTimes.stream().distinct().count() == 1 ? refreshTimes.getFirst()
                            : Optional.<Instant>empty();
            var provenance = new CompositionProvenance(account, fund, clock.instant(), refresh, Optional.empty());
            var composition = new FundComposition(provenance, Denominator.UNKNOWN, ExposureBasis.UNKNOWN,
                            Completeness.UNKNOWN, constituents);
            return new CompositionResult(Status.RECEIVED, Optional.of(composition), Optional.empty());
        }
        catch (IOException | RuntimeException e)
        {
            return failure(Status.MALFORMED_RESPONSE);
        }
    }

    private static boolean inconsistentDuplicate(String isin, BigDecimal nominal,
                    BigDecimal percent, Map<String, BigDecimal> nominalByInstrument,
                    Map<String, BigDecimal> weightByInstrument)
    {
        var previousNominal = nominalByInstrument.putIfAbsent(isin, nominal);
        var previousWeight = weightByInstrument.putIfAbsent(isin, percent);
        return previousNominal != null && previousWeight != null && previousNominal.compareTo(nominal) == 0
                        && previousWeight.compareTo(percent) != 0;
    }

    private static Optional<String> text(JsonObject row, String field)
    {
        var value = row.get(field);
        if (value == null || value.isJsonNull())
            return Optional.empty();
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException();
        return Optional.of(value.getAsString());
    }

    /** Like {@link #text}, but a value of another type counts as absent. */
    private static Optional<String> optionalText(JsonObject row, String field)
    {
        var value = row.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        ? Optional.of(value.getAsString())
                        : Optional.empty();
    }

    /** Like {@link #number}, but a value of another type counts as absent. */
    private static Optional<BigDecimal> optionalNumber(JsonObject row, String field)
    {
        var value = row.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                        ? Optional.of(new BigDecimal(value.getAsString()))
                        : Optional.empty();
    }

    private static Optional<BigDecimal> number(JsonObject row, String field)
    {
        JsonElement value = row.get(field);
        if (value == null || value.isJsonNull())
            return Optional.empty();
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException();
        return Optional.of(new BigDecimal(value.getAsString()));
    }

    private Optional<Instant> retryAfter(Optional<String> header)
    {
        if (header.isEmpty())
            return Optional.empty();
        try
        {
            var value = header.get().trim();
            if (value.matches("[0-9]+"))
                return Optional.of(clock.instant().plusSeconds(Long.parseLong(value)));
            return Optional.of(ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        }
        catch (RuntimeException e)
        {
            return Optional.empty();
        }
    }

    private static CompositionResult failure(Status status)
    {
        return new CompositionResult(status, Optional.empty(), Optional.empty());
    }

    private static void checkAccount(ProviderAccount account)
    {
        if (!ID.equals(account.providerId()))
            throw new IllegalArgumentException();
    }

    /** Shared GET transport; replaceable for tests through the constructor. */
    private static final HttpGet HTTP = new JdkHttpGet(Version.userAgent());

    /** Adapts a GET transport to this provider's {@link Transport}. */
    public static Transport transport(HttpGet http)
    {
        return (uri, authorization) -> {
            var response = http.get(uri, Map.of("Authorization", authorization, "Accept", "application/json"));
            // Error bodies are not needed and may reflect credentials.
            return new Response(response.status(), response.status() == 200 ? response.body() : null,
                            response.header("Retry-After"));
        };
    }

    private static Response download(URI uri, String authorization) throws IOException
    {
        return transport(HTTP).get(uri, authorization);
    }
}
