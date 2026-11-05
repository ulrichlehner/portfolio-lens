package io.portfoliolens.source;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.portfoliolens.http.HttpGet;
import io.portfoliolens.source.PpException.Kind;
import io.portfoliolens.source.PpModel.ApiVersion;
import io.portfoliolens.source.PpModel.Holding;
import io.portfoliolens.source.PpModel.Holdings;
import io.portfoliolens.source.PpModel.Instrument;
import io.portfoliolens.source.PpModel.MonetaryValue;
import io.portfoliolens.source.PpModel.PpFile;
import io.portfoliolens.source.PpModel.Price;

/**
 * Reads Portfolio Performance through its REST API v1. Only GET requests exist here, no request
 * carries an {@code Origin} header, and unknown response fields and open-set values are ignored.
 */
public final class PpClient
{
    private static final String PROBLEMS = "https://portfolio-performance.info/rest/problems/";
    private static final Pattern IPV4_LOOPBACK = Pattern.compile("127(\\.\\d{1,3}){3}");

    private final URI base;
    private final String token;
    private final HttpGet http;

    /**
     * @param base
     *            the PP base URL, for example {@code http://127.0.0.1:5712}; validated by
     *            {@link #validateBaseUrl}
     */
    public PpClient(URI base, String token, HttpGet http)
    {
        this.base = validateBaseUrl(base);
        this.token = Objects.requireNonNull(token);
        this.http = Objects.requireNonNull(http);
    }

    /** Accepts only {@code http} to a loopback IP literal or {@code localhost}, without path. */
    public static URI validateBaseUrl(URI uri)
    {
        if (uri == null || !"http".equals(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("must be an http URL such as http://127.0.0.1:5712");
        var host = uri.getHost().toLowerCase(Locale.ROOT);
        var loopback = "localhost".equals(host) || IPV4_LOOPBACK.matcher(host).matches() || "[::1]".equals(host)
                        || "::1".equals(host);
        if (!loopback)
            throw new IllegalArgumentException(
                            "must name a loopback host (127.0.0.1, [::1] or localhost); PP answers only those");
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("must not contain user info, query or fragment");
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.isEmpty() && !"/".equals(path))
            throw new IllegalArgumentException("must not contain a path; Lens adds /v1 itself");
        return URI.create("http://" + uri.getRawAuthority());
    }

    public ApiVersion version() throws PpException
    {
        var json = get("/v1/version", false, "version");
        return new ApiVersion(string(json, "apiVersion", "version"), optionalString(json, "applicationVersion"));
    }

    public List<PpFile> files() throws PpException
    {
        var json = get("/v1/files", true, "files");
        return items(json, "files", item -> new PpFile(string(item, "id", "files"), optionalString(item, "alias"),
                        string(item, "label", "files"), string(item, "path", "files")));
    }

    public Holdings holdings(String file, Optional<String> reportingCurrency) throws PpException
    {
        var query = reportingCurrency.map(c -> "?reportingCurrency=" + encode(c)).orElse("");
        var json = get("/v1/files/" + encode(file) + "/holdings" + query, true, "holdings");
        var items = items(json, "holdings", item -> new Holding(string(item, "type", "holdings"),
                        string(item, "uuid", "holdings"), string(item, "name", "holdings"),
                        optionalNumber(item, "shares"), optionalPrice(item), money(item, "valuation", "holdings"),
                        number(item, "weight", "holdings"), optionalMoney(item, "localValuation")));
        return new Holdings(date(json, "date", "holdings"), string(json, "reportingCurrency", "holdings"),
                        money(json, "totalAssets", "holdings"), items);
    }

    public List<Instrument> instruments(String file) throws PpException
    {
        var json = get("/v1/files/" + encode(file) + "/instruments", true, "instruments");
        return items(json, "instruments", item -> new Instrument(string(item, "uuid", "instruments"),
                        string(item, "name", "instruments"), optionalString(item, "currencyCode"),
                        optionalString(item, "isin"), optionalString(item, "wkn"),
                        optionalString(item, "tickerSymbol")));
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonObject get(String path, boolean authenticated, String endpoint) throws PpException
    {
        HttpGet.Response response;
        try
        {
            var headers = authenticated ? Map.of("Authorization", "Bearer " + token, "Accept", "application/json")
                            : Map.of("Accept", "application/json");
            response = http.get(base.resolve(path), headers);
        }
        catch (ConnectException | HttpConnectTimeoutException e)
        {
            throw new PpException(Kind.UNREACHABLE, "Portfolio Performance is not reachable at " + base
                            + ": PP is not running, or its REST API is disabled (Preferences > MCP Server & REST API)");
        }
        catch (IOException e)
        {
            // through a port forwarder a missing PP shows as a closed connection, not as refused
            throw new PpException(Kind.UNREACHABLE, "Request to Portfolio Performance at " + base + " failed ("
                            + e.getClass().getSimpleName() + "): PP is probably not running, or its REST API is"
                            + " disabled (Preferences > MCP Server & REST API)");
        }
        if (response.status() != 200)
            throw problem(response, endpoint);
        try
        {
            var element = JsonParser.parseString(response.body());
            if (!element.isJsonObject())
                throw malformed(endpoint, "response is not a JSON object");
            return element.getAsJsonObject();
        }
        catch (JsonParseException e)
        {
            throw malformed(endpoint, "response is not valid JSON");
        }
    }

    private static PpException problem(HttpGet.Response response, String endpoint)
    {
        var type = "";
        try
        {
            var json = JsonParser.parseString(response.body());
            if (json.isJsonObject() && json.getAsJsonObject().has("type"))
                type = json.getAsJsonObject().get("type").getAsString();
        }
        catch (RuntimeException e)
        {
            // An unreadable problem body is treated by its HTTP status.
        }
        var status = response.status();
        if (status == 401)
            return new PpException(Kind.UNAUTHORIZED, "Portfolio Performance rejected the token (HTTP 401). Create a"
                            + " new token in PP (Preferences > MCP Server & REST API > Add client) and configure it.");
        if (status == 403)
            return new PpException(Kind.FORBIDDEN, "Portfolio Performance refused the request (HTTP 403"
                            + (type.isEmpty() ? "" : ", " + type.replace(PROBLEMS, "")) + ").");
        if (status == 404)
            return new PpException(Kind.NOT_FOUND, "Not found at the " + endpoint + " endpoint (HTTP 404): the file"
                            + " is unknown or not enabled for the REST API in PP.");
        if (status == 409 && type.equals(PROBLEMS + "file-not-open"))
            return new PpException(Kind.FILE_NOT_OPEN,
                            "The configured file is not open in Portfolio Performance. Open it in PP.");
        if (status == 409 && type.equals(PROBLEMS + "ambiguous-alias"))
            return new PpException(Kind.AMBIGUOUS_ALIAS,
                            "The configured alias matches several files. Configure the file id instead.");
        return new PpException(Kind.HTTP_ERROR, "Portfolio Performance answered HTTP " + status + " at the "
                        + endpoint + " endpoint.");
    }

    private static PpException malformed(String endpoint, String what)
    {
        return new PpException(Kind.MALFORMED, "Unexpected response from the PP " + endpoint + " endpoint: " + what);
    }

    /** Thrown inside parsing lambdas and converted back to a checked exception. */
    private static final class Malformed extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final PpException exception;

        Malformed(PpException exception)
        {
            super(null, null, false, false);
            this.exception = exception;
        }
    }

    private static <T> List<T> items(JsonObject json, String endpoint, Function<JsonObject, T> parser)
                    throws PpException
    {
        var items = json.get("items");
        if (items == null || !items.isJsonArray())
            throw malformed(endpoint, "missing field 'items'");
        var result = new ArrayList<T>();
        try
        {
            for (var item : items.getAsJsonArray())
            {
                if (!item.isJsonObject())
                    throw malformed(endpoint, "an item is not an object");
                result.add(parser.apply(item.getAsJsonObject()));
            }
        }
        catch (Malformed e)
        {
            throw e.exception;
        }
        return result;
    }

    private static JsonElement field(JsonObject json, String name)
    {
        var value = json.get(name);
        return value == null || value.isJsonNull() ? null : value;
    }

    private static String string(JsonObject json, String name, String endpoint)
    {
        var value = field(json, name);
        if (value == null || !value.isJsonPrimitive())
            throw new Malformed(malformed(endpoint, "missing field '" + name + "'"));
        return value.getAsString();
    }

    private static Optional<String> optionalString(JsonObject json, String name)
    {
        var value = field(json, name);
        return value != null && value.isJsonPrimitive() && !value.getAsString().isBlank()
                        ? Optional.of(value.getAsString())
                        : Optional.empty();
    }

    private static BigDecimal number(JsonObject json, String name, String endpoint)
    {
        var value = field(json, name);
        try
        {
            if (value == null || !value.isJsonPrimitive())
                throw new Malformed(malformed(endpoint, "missing field '" + name + "'"));
            return value.getAsBigDecimal();
        }
        catch (NumberFormatException e)
        {
            throw new Malformed(malformed(endpoint, "field '" + name + "' is not a number"));
        }
    }

    private static Optional<BigDecimal> optionalNumber(JsonObject json, String name)
    {
        var value = field(json, name);
        if (value == null)
            return Optional.empty();
        return Optional.of(number(json, name, "holdings"));
    }

    private static MonetaryValue money(JsonObject json, String name, String endpoint)
    {
        var value = field(json, name);
        if (value == null || !value.isJsonObject())
            throw new Malformed(malformed(endpoint, "missing field '" + name + "'"));
        var object = value.getAsJsonObject();
        return new MonetaryValue(number(object, "value", endpoint), string(object, "currency", endpoint));
    }

    private static Optional<MonetaryValue> optionalMoney(JsonObject json, String name)
    {
        return field(json, name) == null ? Optional.empty() : Optional.of(money(json, name, "holdings"));
    }

    private static Optional<Price> optionalPrice(JsonObject json)
    {
        var value = field(json, "price");
        if (value == null || !value.isJsonObject())
            return Optional.empty();
        var object = value.getAsJsonObject();
        var date = optionalString(object, "date").map(text -> {
            try
            {
                return LocalDate.parse(text);
            }
            catch (DateTimeParseException e)
            {
                return null;
            }
        });
        return Optional.of(new Price(number(object, "value", "holdings"), string(object, "currency", "holdings"),
                        date));
    }

    private static LocalDate date(JsonObject json, String name, String endpoint) throws PpException
    {
        try
        {
            return LocalDate.parse(string(json, name, endpoint));
        }
        catch (Malformed e)
        {
            throw e.exception;
        }
        catch (DateTimeParseException e)
        {
            throw malformed(endpoint, "field '" + name + "' is not a date");
        }
    }
}
