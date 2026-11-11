package io.portfoliolens.page;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.portfoliolens.core.LookThroughContribution;
import io.portfoliolens.page.LookThroughDataStatus.FundStatus;
import io.portfoliolens.page.LookThroughDataStatus.Health;
import io.portfoliolens.page.LookThroughGrouping.Grouping;
import io.portfoliolens.page.LookThroughViewPresenter.CategoryRow;
import io.portfoliolens.page.LookThroughViewPresenter.ChartCompany;
import io.portfoliolens.page.LookThroughViewPresenter.ChartModel;
import io.portfoliolens.page.LookThroughViewPresenter.CompanyRow;
import io.portfoliolens.page.LookThroughViewPresenter.ContributionRow;
import io.portfoliolens.page.LookThroughViewPresenter.ExcludedRow;
import io.portfoliolens.page.LookThroughViewPresenter.GroupKind;
import io.portfoliolens.page.LookThroughViewPresenter.GroupRow;
import io.portfoliolens.page.LookThroughViewPresenter.Model;
import io.portfoliolens.page.LookThroughViewPresenter.RowKey;
import io.portfoliolens.page.LookThroughViewPresenter.Segment;
import io.portfoliolens.page.LookThroughViewPresenter.SegmentKind;
import io.portfoliolens.page.PageInput.ProviderState;
import io.portfoliolens.page.TreemapLayout.Rect;

/**
 * Writes the Breakdown as one self-contained {@code index.html}: inline CSS, server-rendered SVG,
 * the table, the data status, the result data as embedded JSON and a short inline script for
 * grouping, sorting, linked selection and the staleness notice. Nothing is loaded from the
 * network, and without scripts the Company grouping is shown in full.
 */
public final class PageWriter
{
    public static final String PAGE = "index.html";
    public static final String TITLE = "Breakdown by Company";

    /** Settings named on the page; their values never appear. */
    private static final String KEY_SETTING = "LENS_EULERPOOL_API_KEY";
    private static final String CONFIRMATION = "LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true";

    private static final int CHART_WIDTH = 640;
    private static final int TREEMAP_HEIGHT = 380;

    /** Company rows rendered into the page; the rest is built from the embedded data on demand. */
    public static final int TOP_ROWS = 200;
    /**
     * Treemap tiles smaller than this area (in chart units, the chart is 640 x 380) are merged
     * into one "Other" tile per group, so the page stays small for thousands of companies.
     */
    public static final double MIN_TILE_AREA = 140;

    private final PageInput input;
    private final LookThroughViewPresenter presenter = new LookThroughViewPresenter();
    private final StringBuilder out = new StringBuilder(256 * 1024);
    /** Company rows of the Company grouping by the identity part of their key. */
    private final Map<List<Object>, Integer> companyIndex = new java.util.HashMap<>();
    private final List<CompanyRow> companies = new ArrayList<>();
    /** Strings of the embedded data, stored once and referenced by index. */
    private final Map<String, Integer> stringIndex = new java.util.LinkedHashMap<>();

    private PageWriter(PageInput input)
    {
        this.input = input;
    }

    /** Renders and replaces the page atomically. */
    public static void write(Path outputDir, PageInput input) throws IOException
    {
        var html = render(input).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(outputDir);
        var target = outputDir.resolve(PAGE);
        var temporary = Files.createTempFile(outputDir, "." + PAGE, ".tmp");
        try
        {
            Files.write(temporary, html);
            try
            {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    public static String render(PageInput input)
    {
        return new PageWriter(input).page();
    }

    private static String e(String text)
    {
        return Html.escape(text);
    }

    private PageWriter add(String... parts)
    {
        for (var part : parts)
            out.append(part);
        return this;
    }

    private String page()
    {
        var models = new java.util.LinkedHashMap<Grouping, Model>();
        for (var grouping : Grouping.values())
            models.put(grouping, presenter.present(input.result(), grouping, input.attributes()));
        var company = models.get(Grouping.COMPANY);
        for (var row : company.rows())
            if (row instanceof CompanyRow c)
            {
                companyIndex.putIfAbsent(identity(c.key()), companies.size());
                companies.add(c);
            }

        add("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n",
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n",
                        "<meta name=\"generator\" content=\"Portfolio Lens ", e(input.version()), "\">\n",
                        // set before the first paint, so styles that need the script apply at once
                        "<title>", TITLE, "</title>\n<script>document.documentElement.className='js'</script>\n<style>",
                        Style.CSS, "</style>\n</head>\n<body>\n");
        header(company);
        banner();
        summary(company);
        coverage(company);
        var companyNotices = noticeLines(company);
        for (var model : models.values())
        {
            int gi = 0;
            for (var row : model.rows())
                if (row instanceof GroupRow group)
                    groupIndex.put(group.key(), gi++);
        }
        add("<main class=\"panes\">\n<div class=\"chart-pane\" data-tab=\"treemap\">");
        tabs();
        for (var entry : models.entrySet())
            charts(entry.getKey(), entry.getValue());
        overlap(netCash(company).isPresent());
        add("</div>\n<div class=\"table-pane\">");
        for (var entry : models.entrySet())
            view(entry.getKey(), entry.getValue(), companyNotices);
        add("</div>\n</main>\n");
        dataStatus(company);
        add("<footer><p>Portfolio Lens is an independent project. It is not part of Portfolio Performance and is not",
                        " supported by the Portfolio Performance maintainers. It is not affiliated with any data provider.",
                        " Provider permissions and data completeness are not verified.</p>",
                        "<p>Generated by Portfolio Lens ", e(input.version()), ".</p></footer>\n");
        add("<script type=\"application/json\" id=\"lens-data\">", Html.scriptJson(json(models).toString()),
                        "</script>\n<script>", Script.JS, "</script>\n</body>\n</html>\n");
        return out.toString();
    }

    private String time(java.time.Instant instant)
    {
        return Values.DateTime.format(LocalDateTime.ofInstant(instant, input.zone()));
    }

    /**
     * The bar that stays at the top: heading, grouping selector, search and application link in
     * its first line; file, valuation date, generation time and the staleness notice in its
     * second. Both lines have a fixed height, so the notice moves nothing when it appears.
     */
    private void header(Model company)
    {
        add("<header class=\"topbar\"><div class=\"bar-main\"><h1 id=\"heading\">", TITLE, "</h1>",
                        "<label for=\"grouping\">", e(Texts.LookThroughBreakdownBy), "</label> <select id=\"grouping\" disabled>");
        for (var grouping : Grouping.values())
            add("<option value=\"", grouping.name(), "\">", e(grouping.label()), "</option>");
        add("</select><input type=\"search\" id=\"search\" class=\"needs-js\" placeholder=\"Find company\"",
                        " aria-label=\"Find company\" autocomplete=\"off\" spellcheck=\"false\">", appLink(), "</div>",
                        "<p class=\"meta\"><span id=\"stale\" class=\"stale\" hidden></span>Portfolio Lens &middot; ",
                        e(input.fileLabel()), " &middot; ", e(company.summary().asOfText()), " &middot; Generated ",
                        "<time id=\"generated\" datetime=\"", input.generatedAt().toString(), "\">",
                        e(time(input.generatedAt())), "</time></p></header>\n");
    }

    private String appLink()
    {
        return input.appLink().map(link -> "<p class=\"app-link\"><a href=\"" + e(link.url()) + "\" rel=\"noopener\">"
                        + e(link.label()) + "</a></p>").orElse("");
    }

    private void banner()
    {
        var kind = input.banner();
        if (kind.isEmpty())
            return;
        var text = switch (kind.get())
        {
            case UNCONFIRMED -> "The Eulerpool API key has no effect: provider data is requested and used only after"
                            + " you confirm that your Eulerpool plan allows storing its data locally. Add the setting "
                            + CONFIRMATION + ".";
            case NO_KEY -> Texts.LookThroughBannerNoKey + " Set " + KEY_SETTING + " or " + KEY_SETTING + "_FILE.";
            case SUSPENDED -> Texts.LookThroughBannerSuspended + " Change " + KEY_SETTING
                            + " to a valid key; the next run retries.";
            case EMPTY -> Texts.LookThroughLabelEmptyPortfolio;
        };
        var cls = kind.get() == LookThroughBanner.Kind.EMPTY ? "banner info" : "banner warning";
        add("<div class=\"", cls, "\" role=\"alert\" id=\"banner\" data-kind=\"", kind.get().name(), "\">",
                        "<p>", e(text), "</p>");
        if (kind.get() == LookThroughBanner.Kind.UNCONFIRMED)
            add("<p><code>", CONFIRMATION, "</code></p>");
        add("</div>\n");
    }

    private void summary(Model model)
    {
        var s = model.summary();
        add("<section class=\"summary\"><dl>");
        item(Texts.LookThroughSummaryValue, s.valueText(), Optional.empty());
        item(Texts.LookThroughSummaryResolved, s.resolvedText().orElse("–"), Optional.of(model.completenessText()));
        netCash(model).ifPresent(row -> item(Texts.LookThroughCategoryNetCash, amountAndShare(model, row),
                        Optional.of(Texts.LookThroughNetCashTooltip)));
        item(Texts.LookThroughSummaryTopTen, s.topTenText().orElse("–"), Optional.empty());
        s.fundsText().ifPresent(text -> item(Texts.LookThroughSummaryFunds, text, Optional.empty()));
        s.effective().ifPresent(effective -> item(Texts.LookThroughSummaryConcentration, effective.text(),
                        Optional.of(effective.tooltip())));
        s.largest().ifPresent(largest -> item(Texts.LookThroughSummaryLargest, largest.shareText(),
                        Optional.of(Texts.LookThroughSummaryLargestTooltip), largest.name()));
        add("</dl>");
        add("<details class=\"notices", s.attention() ? " attention" : "", "\"><summary>",
                        s.attention() ? "&#9888; " : "&#9432; ", e(model.completenessText()), "</summary>");
        add("<ul>");
        for (var line : noticeLines(model))
            if (!line.equals(model.completenessText()))
                add("<li>", e(line), "</li>");
        if (input.accountCash().getAmount() != 0)
            add("<li>", e("Account cash not included: "
                            + LookThroughViewPresenter.formatAmount(BigDecimal.valueOf(input.accountCash().getAmount()),
                                            input.accountCash().getCurrencyCode())), "</li>");
        if (!input.unsupportedHoldingTypes().isEmpty())
            add("<li>", e(input.unsupportedHoldingTypes().size() + " holding(s) of a type Lens does not know were not"
                            + " analysed: " + String.join(", ", new LinkedHashSet<>(input.unsupportedHoldingTypes()))),
                            "</li>");
        add("<li>", e("Current valuations are combined with the composition dates shown in the data status."),
                        "</li></ul></details></section>\n");
    }

    /** The net cash of funds row of the table, when the result has net cash. */
    static Optional<CategoryRow> netCash(Model model)
    {
        return model.rows().stream().filter(CategoryRow.class::isInstance).map(CategoryRow.class::cast)
                        .filter(row -> row.category() == LookThroughContribution.Category.NET_CASH).findFirst();
    }

    private static String amountAndShare(Model model, CategoryRow row)
    {
        return LookThroughViewPresenter.formatAmount(row.value(), model.currencyCode())
                        + row.shareText().map(text -> " (" + text + ")").orElse("");
    }

    /** The note under a chart that does not draw the net cash of funds. */
    private void netCashNote(Model model)
    {
        netCash(model).ifPresent(row -> add("<p class=\"chart-note\">",
                        e(java.text.MessageFormat.format(Texts.LookThroughNetCashNotShown,
                                        LookThroughViewPresenter.formatAmount(row.value(), model.currencyCode()),
                                        row.shareText().orElse("–"))),
                        "</p>"));
    }

    private static List<String> noticeLines(Model model)
    {
        var lines = new ArrayList<String>();
        for (var line : model.summary().indicatorText().split("\n\n"))
            if (!line.isBlank())
                lines.add(line);
        return lines;
    }

    private void item(String label, String value, Optional<String> tooltip)
    {
        item(label, value, tooltip, "");
    }

    /** A summary figure; the note is a second, smaller line below the value. */
    private void item(String label, String value, Optional<String> tooltip, String note)
    {
        add("<div><dt>", e(label), "</dt><dd><span", tooltip.map(t -> " title=\"" + e(t) + "\"").orElse(""), ">",
                        e(value), "</span>", tooltip.map(PageWriter::hint).orElse(""),
                        note.isEmpty() ? "" : "<small>" + e(note) + "</small>", "</dd></div>");
    }

    /** An explanation that opens on click, tap or keyboard, laid over the page so nothing moves. */
    private static String hint(String text)
    {
        return "<details class=\"hint\"><summary aria-label=\"Explanation\">&#9432;</summary><p>" + e(text)
                        + "</p></details>";
    }

    private void coverage(Model model)
    {
        add("<section class=\"coverage\"><h2>", e(Texts.LookThroughCoverageCaption.replace(":", "")), "</h2>");
        if (model.coverage().isEmpty())
        {
            add("<p>", e(model.empty() ? Texts.LookThroughLabelEmptyPortfolio : Texts.LookThroughCoverageUnavailable),
                            "</p></section>\n");
            return;
        }
        var coverage = model.coverage().get();
        var width = 1000;
        var widths = coverage.widths(width, 4);
        add("<svg class=\"bar\" viewBox=\"0 0 ", String.valueOf(width), " 24\" preserveAspectRatio=\"none\" role=\"img\"",
                        " aria-label=\"", e(Texts.LookThroughCoverageCaption), "\">");
        int x = 0;
        for (int i = 0; i < widths.length; i++)
        {
            if (widths[i] <= 0)
                continue;
            var segment = coverage.segments().get(i);
            add("<rect class=\"cov-", segmentClass(segment.kind()), "\" x=\"", String.valueOf(x), "\" y=\"0\" width=\"",
                            String.valueOf(widths[i]), "\" height=\"24\"><title>", e(segmentTitle(segment)),
                            "</title></rect>");
            x += widths[i];
        }
        add("</svg><ul class=\"legend\">");
        for (var segment : coverage.legend())
            add("<li title=\"", e(segmentTitle(segment)), "\"><span class=\"swatch cov-", segmentClass(segment.kind()),
                            "\"></span>", e(segment.label()), " ", e(segment.shareText()), " <span class=\"legend-amount\">",
                            e(segment.valueText()), "</span></li>");
        add("</ul>");
        netCashNote(model);
        add("</section>\n");
    }

    private static String segmentTitle(Segment segment)
    {
        return segment.label() + ": " + segment.valueText() + ", " + segment.shareText();
    }

    private static String segmentClass(SegmentKind kind)
    {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The chart kinds of the chart pane; the script turns the buttons into tabs. */
    private void tabs()
    {
        add("<div class=\"tabs needs-js\" role=\"tablist\" aria-label=\"Chart\">");
        var kinds = List.of("treemap", "bars", "overlap");
        var labels = List.of(Texts.LookThroughChartTreemap, Texts.LookThroughChartTopCompanies, Texts.LookThroughChartOverlap);
        for (int i = 0; i < kinds.size(); i++)
            add("<button type=\"button\" role=\"tab\" id=\"tab-", kinds.get(i), "\" data-chart=\"", kinds.get(i),
                            "\" aria-selected=\"", i == 0 ? "true" : "false", "\" tabindex=\"", i == 0 ? "0" : "-1", "\">",
                            e(labels.get(i)), "</button>");
        add("</div>");
    }

    private static String hiddenUnlessCompany(Grouping grouping)
    {
        return grouping == Grouping.COMPANY ? "" : " hidden";
    }

    /**
     * The treemap and the bar chart of a grouping. Every figure has the same parts of the same
     * height: a line above the chart, the chart in a 640 x 380 box, a legend line and, when the
     * result has net cash, a note line. The pane therefore keeps its height on every tab.
     */
    private void charts(Grouping grouping, Model model)
    {
        var chart = model.chart();
        var note = netCash(model).isPresent();
        add("<figure class=\"chart\" data-chart=\"treemap\" data-grouping=\"", grouping.name(), "\"",
                        hiddenUnlessCompany(grouping), "><div class=\"chart-head overview\"><button type=\"button\"",
                        " class=\"back needs-js\" hidden>&#8249; Back</button><nav class=\"crumbs\"",
                        " aria-label=\"Treemap level\"></nav><span class=\"chart-caption\">",
                        e(Texts.LookThroughChartTreemap), "</span></div><div class=\"chart-box\">");
        if (chart.isEmpty())
            add("<p class=\"empty\">", e(Texts.LookThroughChartEmpty), "</p>");
        else
            treemap(grouping, chart);
        // the tile color encodes the direct part on a continuous green-to-blue scale
        add("</div><p class=\"chart-legend\"><span class=\"legend-caption\">", e(Texts.LookThroughChartTileColor),
                        "</span> ", e(Texts.LookThroughChartHeldDirectly),
                        " <span class=\"gradient\" style=\"background:linear-gradient(to right,",
                        ChartColors.hex(ChartColors.DIRECT), ",", ChartColors.hex(ChartColors.INDIRECT),
                        ")\"></span> ", e(Texts.LookThroughChartThroughFunds), "</p>");
        netCashNote(model);
        add("</figure>");
        add("<figure class=\"chart\" data-chart=\"bars\" data-grouping=\"", grouping.name(), "\"",
                        hiddenUnlessCompany(grouping), "><div class=\"chart-head\"><span class=\"chart-caption\">",
                        e(Texts.LookThroughChartTopCompanies), "</span></div><div class=\"chart-box\">");
        if (chart.isEmpty())
            add("<p class=\"empty\">", e(Texts.LookThroughChartEmpty), "</p>");
        else
            bars(grouping, chart, model.currencyCode());
        add("</div><p class=\"chart-legend\"><span class=\"legend-caption\">", e(Texts.LookThroughChartBarColor),
                        "</span> <span class=\"swatch\" style=\"background:", ChartColors.hex(ChartColors.DIRECT),
                        "\"></span>", e(Texts.LookThroughChartHeldDirectly), " <span class=\"swatch\" style=\"background:",
                        ChartColors.hex(ChartColors.INDIRECT), "\"></span>", e(Texts.LookThroughChartThroughFunds), "</p>",
                        note ? "<p class=\"chart-note\">&nbsp;</p>" : "", "</figure>\n");
    }

    /** The notices and the table of a grouping. */
    private void view(Grouping grouping, Model model, List<String> companyNotices)
    {
        add("<section class=\"view\" id=\"view-", grouping.name(), "\" data-grouping=\"", grouping.name(),
                        "\" data-title=\"", e(heading(grouping)), "\"", hiddenUnlessCompany(grouping), ">");
        for (var line : noticeLines(model))
            if (!companyNotices.contains(line))
                add("<p class=\"view-notice\">", e(line), "</p>");
        table(grouping, model);
        add("</section>\n");
    }

    /** The page heading of a grouping, for example "Breakdown by Sector". */
    static String heading(Grouping grouping)
    {
        return "Breakdown by " + grouping.label();
    }

    /** The company identity part of a row key: the kind and the identity, without suffixes. */
    private static List<Object> identity(RowKey key)
    {
        var parts = key.parts();
        return List.copyOf(parts.subList(0, Math.min(2, parts.size())));
    }

    private static String hash(Object value)
    {
        try
        {
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 5);
        }
        catch (java.security.NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Row id of a company in the Company table; members of a grouping prefix it with the grouping. */
    private static String companyId(RowKey key)
    {
        return "c" + hash(identity(key).toString());
    }

    private static String rowId(Grouping grouping, RowKey key)
    {
        if (!key.parts().isEmpty() && key.parts().get(0) == LookThroughViewPresenter.RowKind.COMPANY)
            return grouping == Grouping.COMPANY ? companyId(key)
                            : grouping.name().toLowerCase(Locale.ROOT) + "-" + companyId(key);
        return grouping.name().toLowerCase(Locale.ROOT) + "-g" + hash(key.parts().toString());
    }

    private int string(String text)
    {
        return stringIndex.computeIfAbsent(text, ignored -> stringIndex.size());
    }

    private static String fraction(double value)
    {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String num(double value)
    {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String tileTitle(ChartCompany company, String currency)
    {
        var lines = new ArrayList<String>();
        lines.add(company.label());
        lines.add(LookThroughViewPresenter.formatAmount(company.total(), currency)
                        + company.shareText().map(s -> ", " + s).orElse(""));
        lines.add(Texts.LookThroughColumnDirect + ": " + LookThroughViewPresenter.formatAmount(company.direct(), currency));
        lines.add(Texts.LookThroughColumnIndirect + ": "
                        + LookThroughViewPresenter.formatAmount(company.indirect(), currency));
        company.detail().ifPresent(lines::add);
        return String.join("\n", lines);
    }

    /** The members of a tile area, with the small ones merged into one "Other" entry. */
    record Merged(List<ChartCompany> shown, List<ChartCompany> others, int otherCount, double otherWeight,
                    double otherDirect, double threshold)
    {
    }

    /**
     * The text of a merged tile: count, total share and the size limit of its companies, for
     * example "3,882 companies, 33.1%, each below 0.05%". All values are in hundredths of the
     * reporting currency; without a denominator only the count is given. The page script builds
     * the same text for zoomed levels (see {@link Script#LAYOUT}).
     */
    static String mergedLabel(int count, double weight, double threshold, Optional<Long> denominator)
    {
        var companies = String.format(Locale.ENGLISH, "%,d", count) + (count == 1 ? " company" : " companies");
        if (denominator.isEmpty() || denominator.get() <= 0)
            return companies;
        var share = weight / denominator.get() * 100;
        return companies + ", " + String.format(Locale.ROOT, "%.1f", share) + "%, each below "
                        + upperPercent(threshold / denominator.get() * 100) + "%";
    }

    /** A percentage rounded up to two significant digits, so "below" stays true. */
    static String upperPercent(double percent)
    {
        if (!(percent > 0))
            return "0";
        var exponent = (int) Math.floor(Math.log10(percent));
        var step = Math.pow(10, exponent - 1);
        var up = Math.ceil(percent / step - 1e-9) * step;
        var decimals = Math.max(0, 1 - exponent);
        var text = String.format(Locale.ROOT, "%." + decimals + "f", up);
        return text.contains(".") ? text.replaceAll("\\.?0+$", "") : text;
    }

    /** Merged members per grouping and group (-1 for the company view), for the page script. */
    private final Map<String, Map<String, List<Integer>>> mergedMembers = new java.util.LinkedHashMap<>();
    /** Group index in the table and the embedded data, by group row key. */
    private final Map<RowKey, Integer> groupIndex = new java.util.HashMap<>();

    static Merged merge(List<ChartCompany> members, double scale)
    {
        var shown = new ArrayList<ChartCompany>();
        var others = new ArrayList<ChartCompany>();
        int count = 0;
        double weight = 0;
        double direct = 0;
        for (var member : members)
        {
            var value = member.total().doubleValue();
            if (value * scale >= MIN_TILE_AREA)
                shown.add(member);
            else if (value > 0)
            {
                others.add(member);
                count++;
                weight += value;
                direct += value * member.directFraction();
            }
        }
        return new Merged(shown, others, count, weight, direct, scale > 0 ? MIN_TILE_AREA / scale : 0);
    }

    /** A tile of the treemap: a company, or the merged small companies of an area. */
    private record TileItem(ChartCompany company, Merged merged, String zoom, double weight, double directFraction)
    {
    }

    /** {@code zoom} names the merged tile for the page script: the group index, or "all". */
    private List<TileItem> items(Grouping grouping, String zoom, Merged merged)
    {
        var items = new ArrayList<TileItem>();
        merged.shown().forEach(c -> items.add(new TileItem(c, null, zoom, c.total().doubleValue(), c.directFraction())));
        if (merged.otherCount() > 0)
        {
            items.add(new TileItem(null, merged, zoom, merged.otherWeight(),
                            merged.otherWeight() > 0 ? merged.otherDirect() / merged.otherWeight() : 0));
            var indexes = new ArrayList<Integer>();
            for (var other : merged.others())
            {
                var index = companyIndex.get(identity(other.key()));
                if (index != null)
                    indexes.add(index);
            }
            mergedMembers.computeIfAbsent(grouping.name(), ignored -> new java.util.LinkedHashMap<>()).put(zoom, indexes);
        }
        return items;
    }

    private void treemap(Grouping grouping, ChartModel chart)
    {
        var currency = input.result().valuation().valuedSubset().getCurrencyCode();
        var area = new Rect(0, 0, CHART_WIDTH, TREEMAP_HEIGHT);
        add("<svg class=\"treemap\" data-grouping=\"", grouping.name(), "\" viewBox=\"0 0 ", String.valueOf(CHART_WIDTH), " ",
                        String.valueOf(TREEMAP_HEIGHT), "\" role=\"group\" aria-label=\"", e(Texts.LookThroughChartTreemap), "\">",
                        "<rect class=\"tm-bg\" x=\"0\" y=\"0\" width=\"", String.valueOf(CHART_WIDTH), "\" height=\"",
                        String.valueOf(TREEMAP_HEIGHT), "\"/>");
        if (chart.isGrouped())
        {
            var total = chart.groups().stream().mapToDouble(g -> g.group().total().doubleValue()).sum();
            var scale = total > 0 ? area.area() / total : 0;
            var layout = TreemapLayout.grouped(chart.groups(), g -> g.group().total().doubleValue(),
                            g -> items(grouping, String.valueOf(groupIndex.getOrDefault(g.group().key(), -1)),
                                            merge(g.companies(), scale)),
                            TileItem::weight, item -> item.company() == null, area);
            for (var group : layout)
            {
                var r = group.rect();
                var id = rowId(grouping, group.group().group().key());
                var gi = String.valueOf(groupIndex.getOrDefault(group.group().group().key(), -1));
                add("<g><rect class=\"tm-group\" x=\"", num(r.x()), "\" y=\"", num(r.y()), "\" width=\"",
                                num(r.width()), "\" height=\"", num(r.height()), "\"/>");
                if (group.header())
                    label(r.x() + 3, r.y() + 12, r.width() - 6, group.group().group().label()
                                    + group.group().group().shareText().map(s -> " " + s).orElse(""), "tm-group-label");
                add("</g>");
                for (var tile : group.members())
                    tile(grouping, tile.item(), tile.rect(), currency, false);
                // one area over the whole group takes click and focus: it shows the group's companies
                var title = tileTitle(group.group().group(), currency) + "\nClick to show its companies";
                add("<a href=\"#", id, "\" class=\"tm-zoom\" data-row=\"", id, "\" data-zoom=\"", gi,
                                "\" aria-label=\"", e(title.replace('\n', ' ')), "\"><title>", e(title),
                                "</title><rect class=\"tm-overlay\" x=\"", num(r.x()), "\" y=\"", num(r.y()),
                                "\" width=\"", num(r.width()), "\" height=\"", num(r.height()), "\"/></a>");
            }
        }
        else
        {
            var total = chart.companies().stream().mapToDouble(c -> c.total().doubleValue()).sum();
            var scale = total > 0 ? area.area() / total : 0;
            for (var tile : TreemapLayout.squarify(items(grouping, "all", merge(chart.companies(), scale)),
                            TileItem::weight, item -> item.company() == null, area))
                tile(grouping, tile.item(), tile.rect(), currency, true);
        }
        add("</svg>");
    }

    /**
     * @param active
     *            whether the tile takes click and focus itself; in a grouped overview the area of
     *            its group does
     */
    private void tile(Grouping grouping, TileItem item, Rect r, String currency, boolean active)
    {
        if (item.company() == null)
        {
            var merged = item.merged();
            var text = mergedLabel(merged.otherCount(), merged.otherWeight(), merged.threshold(),
                            input.result().valuation().denominator().map(m -> m.getAmount()));
            add("<g class=\"tm-merged\"", active ? " role=\"button\" tabindex=\"0\"" : "", " data-zoom=\"",
                            e(item.zoom()), "\" aria-label=\"", e(text + ". Press Enter to explore."), "\"><title>",
                            e(text + "\nClick to explore"), "</title><rect class=\"tm-tile tm-other\" x=\"", num(r.x()),
                            "\" y=\"", num(r.y()), "\" width=\"", num(r.width()), "\" height=\"", num(r.height()),
                            "\"/>");
            if (r.width() >= 48 && r.height() >= 20)
            {
                label(r.x() + 4, r.y() + 14, r.width() - 8, text, "tm-label tm-merged-label");
                if (r.height() >= 34)
                    label(r.x() + 4, r.y() + 28, r.width() - 8, "Click to explore \u203A", "tm-share tm-merged-label");
            }
            add("</g>");
            return;
        }
        var company = item.company();
        var id = rowId(grouping, company.key());
        add("<a href=\"#", id, "\" data-row=\"", id, "\"", active ? "" : " tabindex=\"-1\"",
                        "><rect class=\"tm-tile\" fill=\"",
                        ChartColors.tileColor(company.directFraction()), "\" x=\"", num(r.x()), "\" y=\"", num(r.y()),
                        "\" width=\"", num(r.width()), "\" height=\"", num(r.height()), "\"><title>",
                        e(tileTitle(company, currency)), "</title></rect>");
        if (r.width() >= 48 && r.height() >= 30)
        {
            label(r.x() + 4, r.y() + 14, r.width() - 8, company.label(), "tm-label");
            company.shareText().ifPresent(share -> label(r.x() + 4, r.y() + 27, r.width() - 8, share, "tm-share"));
        }
        add("</a>");
    }

    /** Text cut to the width, estimated at 6.2 units per character of the 11-unit font. */
    private void label(double x, double y, double width, String text, String cls)
    {
        var max = (int) Math.floor(width / 6.2);
        if (max < 2)
            return;
        var shown = text.length() > max ? text.substring(0, Math.max(1, max - 1)) + "…" : text;
        add("<text class=\"", cls, "\" x=\"", num(x), "\" y=\"", num(y), "\">", e(shown), "</text>");
    }

    private void bars(Grouping grouping, ChartModel chart, String currency)
    {
        var top = chart.top();
        // 20 rows fill the box of the treemap, so both charts have the same size
        var rowHeight = (TREEMAP_HEIGHT - 4.0) / ChartModel.TOP_COMPANIES;
        var labelWidth = 190;
        var valueTexts = top.stream().map(c -> LookThroughViewPresenter.formatAmount(c.total(), currency)
                        + c.shareText().map(s -> "  " + s).orElse("")).toList();
        // the value axis leaves room for the longest label
        var valueRoom = valueTexts.stream().mapToInt(String::length).max().orElse(0) * 6.4 + 8;
        var barRoom = Math.max(60, CHART_WIDTH - labelWidth - valueRoom);
        var max = top.stream().mapToDouble(c -> c.total().doubleValue()).max().orElse(1);
        add("<svg class=\"bars\" viewBox=\"0 0 ", String.valueOf(CHART_WIDTH), " ", String.valueOf(TREEMAP_HEIGHT),
                        "\" role=\"img\" aria-label=\"", e(Texts.LookThroughChartTopCompanies), "\">");
        for (int i = 0; i < top.size(); i++)
        {
            var c = top.get(i);
            var id = rowId(grouping, c.key());
            var y = i * rowHeight + 2;
            var total = c.total().doubleValue() / max * barRoom;
            var direct = c.total().signum() > 0 ? total * c.directFraction() : 0;
            add("<a href=\"#", id, "\" data-row=\"", id, "\"><title>", e(tileTitle(c, currency)), "</title>");
            label(0, y + 13, labelWidth - 6, c.label(), "bar-label");
            add("<rect class=\"bar-direct\" x=\"", String.valueOf(labelWidth), "\" y=\"", num(y + 2),
                            "\" width=\"", num(direct), "\" height=\"14\"/>", "<rect class=\"bar-indirect\" x=\"",
                            num(labelWidth + direct), "\" y=\"", num(y + 2), "\" width=\"",
                            num(Math.max(0, total - direct)), "\" height=\"14\"/>", "<text class=\"bar-value\" x=\"",
                            num(labelWidth + total + 6), "\" y=\"", num(y + 13), "\">", e(valueTexts.get(i)),
                            "</text></a>");
        }
        add("</svg>");
    }

    /** An amount of a table cell: the currency stands in the column head, not in every cell. */
    private static String plain(BigDecimal value, String currency)
    {
        return LookThroughViewPresenter.formatAmount(value, currency).replace(currency, "").trim();
    }

    /**
     * Every breakdown table has the same head with the same fixed column widths, so the columns
     * keep their place when the grouping changes or rows are added.
     */
    private void table(Grouping grouping, Model model)
    {
        var currency = model.currencyCode();
        var second = grouping == Grouping.COMPANY ? Texts.LookThroughColumnCompany
                        : MessageFormat.format(Texts.LookThroughColumnGrouped, grouping.label());
        var unit = " (" + currency + ")";
        add("<table class=\"breakdown\" data-grouping=\"", grouping.name(), "\"><thead><tr><th class=\"rank\">",
                        e(Texts.LookThroughColumnRank), "</th><th class=\"name\" data-sort=\"label\">", e(second),
                        "</th><th class=\"num\" data-sort=\"direct\">", e(Texts.LookThroughColumnDirect + unit),
                        "</th><th class=\"num\" data-sort=\"indirect\">", e(Texts.LookThroughColumnIndirect + unit),
                        "</th><th class=\"num\" data-sort=\"total\">", e(Texts.LookThroughColumnTotal + unit),
                        "</th><th class=\"num share\" data-sort=\"total\" title=\"", e(Texts.LookThroughColumnShare),
                        "\">Share</th></tr></thead>");
        int rank = 0;
        int groupIndex = 0;
        int companyCount = 0;
        for (var row : model.rows())
        {
            if (row instanceof CompanyRow company)
            {
                rank++;
                companyCount++;
                if (rank <= TOP_ROWS)
                    companyBlock(company, rank, currency);
            }
            else if (row instanceof GroupRow group)
            {
                var ranked = group.groupKind() == GroupKind.REGULAR;
                var id = rowId(grouping, group.key());
                add("<tbody class=\"block group\" data-group=\"", String.valueOf(groupIndex++), "\" data-label=\"",
                                e(group.label()), "\" data-direct=\"", group.direct().toPlainString(),
                                "\" data-indirect=\"", group.indirect().toPlainString(), "\" data-total=\"",
                                group.total().toPlainString(), "\"><tr id=\"", id, "\" class=\"top\"><td class=\"rank\">",
                                ranked ? String.valueOf(++rank) : "",
                                "</td><td><button type=\"button\" class=\"toggle\" aria-expanded=\"false\">",
                                e(group.label()), " (", String.valueOf(group.count()), ")</button></td>");
                amounts(group.direct(), group.indirect(), group.total(), group.shareText(), currency);
                add("</tr></tbody>");
            }
            else if (row instanceof CategoryRow category)
            {
                // categories that are empty or round to zero in the currency are not shown
                if (category.value().setScale(0, RoundingMode.HALF_UP).signum() == 0)
                    continue;
                add("<tbody class=\"block category fixed\"><tr class=\"top\"><td class=\"rank\"></td><td>");
                if (category.details().isEmpty())
                    add(e(category.label()));
                else
                    add("<details><summary>", e(category.label()), "</summary></details>");
                add("</td><td></td><td></td><td class=\"num\">", e(plain(category.value(), currency)),
                                "</td><td class=\"num\">", e(category.shareText().orElse("")), "</td></tr>");
                if (!category.details().isEmpty())
                {
                    add("<tr class=\"detail\"><td></td><td colspan=\"5\"><ul class=\"contrib\">");
                    for (var detail : category.details())
                        add("<li>", e(detail.label()), " &middot; ", e(detail.sourceText()),
                                        detail.note().isBlank() ? "" : " &middot; " + e(detail.note()), ": ",
                                        e(LookThroughViewPresenter.formatAmount(detail.value(), currency)),
                                        detail.shareText().map(s -> " (" + e(s) + ")").orElse(""), "</li>");
                    add("</ul></td></tr>");
                }
                add("</tbody>");
            }
            else if (row instanceof ExcludedRow excluded)
            {
                add("<tbody class=\"block excluded fixed\"><tr class=\"top\"><td class=\"rank\"></td><td colspan=\"5\">",
                                e(excluded.label()), " &middot; <span class=\"reason\">", e(excluded.reasonText()),
                                "</span></td></tr></tbody>");
            }
        }
        if (grouping == Grouping.COMPANY && companyCount > TOP_ROWS)
            add("<tbody class=\"more fixed\"><tr><td></td><td colspan=\"5\">Showing the ", String.valueOf(TOP_ROWS),
                            " largest of ", String.valueOf(companyCount),
                            " companies. <button type=\"button\" class=\"show-more\" hidden>Show more</button>",
                            "</td></tr></tbody>");
        add("</table>");
    }

    /**
     * A company row and, in a row of its own over the width of the table, its detail and
     * contributions. The second row is shown while the row is open, without any script.
     */
    private void companyBlock(CompanyRow company, int rank, String currency)
    {
        var id = companyId(company.key());
        add("<tbody class=\"block company\" data-index=\"", String.valueOf(rank - 1), "\"><tr id=\"", id,
                        "\" class=\"top\"><td class=\"rank\">", String.valueOf(rank), "</td><td><details><summary>",
                        e(company.label()), "</summary></details></td>");
        amounts(company.direct(), company.indirect(), company.total(), company.shareText(), currency);
        add("</tr><tr class=\"detail\"><td></td><td colspan=\"5\">");
        company.detail().ifPresent(detail -> add("<p class=\"detail\">", e(detail), "</p>"));
        add("<ul class=\"contrib\">");
        for (ContributionRow contribution : company.contributions())
            add("<li class=\"", contribution.direct() ? "direct" : "indirect", "\">", e(contribution.sourceText()), ": ",
                            e(LookThroughViewPresenter.formatAmount(contribution.value(), currency)),
                            contribution.shareText().map(s -> " (" + e(s) + ")").orElse(""), "</li>");
        add("</ul></td></tr></tbody>");
    }

    private void amounts(BigDecimal direct, BigDecimal indirect, BigDecimal total, Optional<String> share,
                    String currency)
    {
        add("<td class=\"num\">", e(plain(direct, currency)), "</td><td class=\"num\">", e(plain(indirect, currency)),
                        "</td><td class=\"num\">", e(plain(total, currency)), "</td><td class=\"num\">",
                        e(share.orElse("")), "</td>");
    }

    /**
     * The overlap matrix as a chart of the chart pane, in the same parts as the other charts.
     *
     * @param note
     *            whether the other charts have a note line, which this one then keeps empty
     */
    private void overlap(boolean note)
    {
        var model = LookThroughOverlapModel.of(input.overlap());
        add("<figure class=\"chart\" data-chart=\"overlap\"><div class=\"chart-head\"><span class=\"chart-caption\">",
                        e(Texts.LookThroughChartOverlap), "</span></div><div class=\"chart-box\">");
        if (model.isEmpty())
            add("<p class=\"empty\">", e(Texts.LookThroughOverlapNeedTwoFunds), "</p>");
        else
        {
            var n = model.size();
            var cell = 84;
            var labelWidth = 220;
            var width = labelWidth + (n - 1) * cell + 4;
            var height = (n - 1) * cell + 40;
            // scaled into the box; many funds keep a readable cell size and scroll inside the box
            add("<svg class=\"matrix\" style=\"min-width:", String.valueOf(labelWidth + (n - 1) * 48), "px;min-height:",
                            String.valueOf((n - 1) * 48 + 24), "px\" viewBox=\"0 0 ", String.valueOf(width), " ",
                            String.valueOf(height), "\" role=\"img\" aria-label=\"", e(Texts.LookThroughChartOverlap),
                            "\">");
            // lower triangle without diagonal: rows 1..n-1, columns 0..row-1
            for (int row = 1; row < n; row++)
            {
                var y = (row - 1) * cell;
                label(0, y + cell / 2.0 + 4, labelWidth - 8, model.name(row), "mx-label");
                for (int column = 0; column < row; column++)
                {
                    var c = model.cell(row, column);
                    var x = labelWidth + column * cell;
                    add("<g><title>", e(c.tooltip()), "</title><rect class=\"mx-cell\" style=\"--shade:",
                                    fraction(c.shade()), "\" x=\"", String.valueOf(x + 1), "\" y=\"", String.valueOf(y + 1),
                                    "\" width=\"", String.valueOf(cell - 2), "\" height=\"", String.valueOf(cell - 2),
                                    "\"/><text class=\"mx-text\" x=\"", String.valueOf(x + cell / 2), "\" y=\"",
                                    String.valueOf(y + cell / 2), "\">", e(c.text()), "</text><text class=\"mx-detail\" x=\"",
                                    String.valueOf(x + cell / 2), "\" y=\"", String.valueOf(y + cell / 2 + 16), "\">",
                                    e(c.detail()), "</text></g>");
                }
            }
            for (int column = 0; column < n - 1; column++)
            {
                var x = labelWidth + column * cell + 4;
                label(x, (n - 1) * cell + 16, cell - 6, model.name(column), "mx-label");
            }
            add("</svg>");
        }
        add("</div><p class=\"chart-legend\">", model.excludedText().map(PageWriter::e).orElse("&nbsp;"), "</p>",
                        note ? "<p class=\"chart-note\">&nbsp;</p>" : "", "</figure>\n");
    }

    private void dataStatus(Model company)
    {
        var status = input.dataStatus();
        var rows = LookThroughDataStatus.listed(status, company.directlyClassified());
        var clock = Clock.fixed(input.generatedAt(), input.zone());
        var summaryRow = LookThroughDataStatus.summaryRow(rows, clock);
        var unconfirmed = input.provider() == ProviderState.UNCONFIRMED;
        var rowText = unconfirmed ? "Provider data not used: local storage not confirmed"
                        : summaryRow.map(LookThroughDataStatus.SummaryRow::text).orElse("No funds to break down");
        if (input.pendingLookups() > 0)
            rowText += " · " + input.pendingLookups() + " lookup(s) pending for a later run"
                            + input.nextAttempt().map(at -> ", next attempt " + time(at)).orElse("");
        var warning = unconfirmed || summaryRow.map(LookThroughDataStatus.SummaryRow::warning).orElse(false);
        add("<section class=\"status\"><details id=\"data-status\"><summary class=\"", warning ? "warning" : "", "\"",
                        summaryRow.map(r -> " title=\"" + e(r.tooltip()) + "\"").orElse(""), ">",
                        warning ? "&#9888; " : "", e(rowText), "</summary>");
        // the tooltip of the closed row, as text once the data status is open
        summaryRow.map(LookThroughDataStatus.SummaryRow::tooltip).filter(text -> !text.isBlank())
                        .ifPresent(text -> add("<p class=\"status-explanation\">", e(text), "</p>"));
        var companyData = input.provider() == ProviderState.ENABLED
                        ? LookThroughDataStatus.companyText(input.attributes().progress(), input.generatedAt(), input.zone())
                        : Optional.<String>empty();
        var line = LookThroughDataStatus.summaryLine(status, companyData, company.assumedCompanies());
        add("<div class=\"status-line\"><span title=\"", e(line.tooltip()), "\">", e(line.text()), "</span>",
                        line.tooltip().isBlank() ? "" : hint(line.tooltip()), "</div>");
        input.budget().ifPresent(usage -> add("<p class=\"", usage.exhausted() ? "status-warning" : "status-line",
                        "\" id=\"budget\">", usage.exhausted() ? "&#9888; " : "",
                        e("Provider requests: " + usage.used() + " of " + usage.capacity()
                                        + " units used in the last 24 hours"
                                        + (usage.exhausted() ? "; remaining lookups wait"
                                                        + usage.oldestExpiresAt().map(at -> " until " + time(at)).orElse("")
                                                        : "")),
                        "</p>"));
        if (input.provider() == ProviderState.ENABLED)
            line.warningText().ifPresent(text -> add("<div class=\"status-warning\"><span title=\"",
                            e(line.warningTooltip().orElse("")), "\">&#9888; ", e(text), "</span>",
                            line.warningTooltip().filter(t -> !t.isBlank()).map(PageWriter::hint).orElse(""), "</div>"));
        else
            status.limitationText().ifPresent(text -> add("<p class=\"status-warning\">&#9888; ", e(text), "</p>"));
        if (!rows.isEmpty())
        {
            add("<table class=\"funds\"><thead><tr><th>", e(Texts.LookThroughStatusColumnFund), "</th><th>",
                            e(Texts.LookThroughStatusColumnStatus), "</th><th class=\"retrieved\">",
                            e(Texts.LookThroughStatusColumnRetrieved), "</th><th class=\"num\">",
                            e(Texts.LookThroughStatusColumnShare), "</th></tr></thead>");
            for (var fund : rows)
                fundRow(fund, company.decomposedShares(), unconfirmed);
            add("</table>");
        }
        add("</details></section>\n");
    }

    /** A fund of the data status; the lines of its tooltip are also a row that opens below it. */
    private void fundRow(FundStatus fund, Map<Map<String, String>, String> shares, boolean unconfirmed)
    {
        var health = unconfirmed ? Health.NONE : fund.health();
        var statusText = unconfirmed ? "Not looked up: local storage not confirmed"
                        : fund.statusText() + fund.netCashText().map(text -> ", " + Character.toLowerCase(text.charAt(0))
                                        + text.substring(1)).orElse("");
        var tooltip = new ArrayList<String>();
        if (!unconfirmed)
        {
            fund.explanation().ifPresent(tooltip::add);
            tooltip.add(fund.attemptText());
            fund.retryText().ifPresent(tooltip::add);
            fund.providerRefreshedText().ifPresent(tooltip::add);
            fund.holdingsText().ifPresent(tooltip::add);
            fund.freshnessText().ifPresent(tooltip::add);
            fund.sourceDataText().ifPresent(tooltip::add);
            fund.denominatorText().ifPresent(tooltip::add);
            fund.nettingText().ifPresent(tooltip::add);
            fund.netCashText().ifPresent(tooltip::add);
        }
        tooltip.removeIf(String::isBlank);
        var icon = switch (health)
        {
            case OK -> "&#10003;";
            case WARNING -> "&#9888;";
            case ERROR -> "&#10007;";
            case PENDING -> "&#8987;";
            case NONE -> "&#8211;";
        };
        add("<tbody><tr class=\"top\" title=\"", e(String.join("\n", tooltip)), "\"><td>");
        if (tooltip.isEmpty())
            add(e(fund.fundLabel()));
        else
            add("<details><summary>", e(fund.fundLabel()), "</summary></details>");
        add("</td><td class=\"health-", health.name().toLowerCase(Locale.ROOT), "\">", icon, " ", e(statusText),
                        "</td><td>", e(unconfirmed ? "" : fund.retrievedDateText().orElse("")), "</td><td class=\"num\">",
                        e(unconfirmed ? "–" : LookThroughDataStatus.shareText(fund, shares)), "</td></tr>");
        if (!tooltip.isEmpty())
        {
            add("<tr class=\"detail\"><td colspan=\"4\"><ul class=\"contrib\">");
            for (var text : tooltip)
                add("<li>", e(text), "</li>");
            add("</ul></td></tr>");
        }
        add("</tbody>");
    }

    private JsonObject json(Map<Grouping, Model> models)
    {
        var json = new JsonObject();
        json.addProperty("generatedAt", input.generatedAt().toString());
        json.addProperty("version", input.version());
        var company = models.get(Grouping.COMPANY);
        json.addProperty("valuationDate", company.valuationDate().toString());
        json.addProperty("currency", company.currencyCode());
        json.addProperty("providerData", input.provider().name().toLowerCase(Locale.ROOT));
        json.addProperty("pendingLookups", input.pendingLookups());
        json.addProperty("topRows", TOP_ROWS);
        company.denominator().ifPresent(d -> json.addProperty("denominator", d.getAmount()));
        var groupings = new JsonObject();
        for (var grouping : Grouping.values())
            groupings.addProperty(grouping.name(), heading(grouping));
        json.add("headings", groupings);

        // companies in rank order: [id, label, direct, indirect, detail, [[source, value, direct], ...]]
        var rows = new JsonArray();
        for (var c : companies)
        {
            var row = new JsonArray();
            row.add(companyId(c.key()));
            row.add(c.label());
            row.add(amount(c.direct()));
            row.add(amount(c.indirect()));
            row.add(c.detail().map(this::string).orElse(-1));
            var contributions = new JsonArray();
            for (var contribution : c.contributions())
            {
                var item = new JsonArray();
                item.add(string(contribution.sourceText()));
                item.add(amount(contribution.value()));
                item.add(contribution.direct() ? 1 : 0);
                contributions.add(item);
            }
            row.add(contributions);
            rows.add(row);
        }
        json.add("c", rows);

        // members of each group: [company index, factor, detail]; factor below 1 splits by revenue
        var groups = new JsonObject();
        for (var entry : models.entrySet())
        {
            if (entry.getKey() == Grouping.COMPANY)
                continue;
            var list = new JsonArray();
            for (var row : entry.getValue().rows())
                if (row instanceof GroupRow group)
                {
                    var members = new JsonArray();
                    for (var member : group.companies())
                    {
                        var index = companyIndex.get(identity(member.key()));
                        if (index == null)
                            continue;
                        var full = companies.get(index).total();
                        var factor = full.signum() == 0 ? 1d
                                        : member.total().divide(full, 8, RoundingMode.HALF_UP).doubleValue();
                        var item = new JsonArray();
                        item.add(index);
                        item.add(Math.abs(factor - 1) < 1e-9 ? 1 : factor);
                        item.add(member.detail().map(this::string).orElse(-1));
                        members.add(item);
                    }
                    list.add(members);
                }
            groups.add(entry.getKey().name(), list);
        }
        json.add("g", groups);

        // members of merged treemap tiles, by grouping and group ("all" in the company view)
        var merged = new JsonObject();
        mergedMembers.forEach((grouping, byZoom) -> {
            var object = new JsonObject();
            byZoom.forEach((zoom, indexes) -> {
                var list = new JsonArray();
                indexes.forEach(list::add);
                object.add(zoom, list);
            });
            merged.add(grouping, object);
        });
        json.add("m", merged);
        json.addProperty("minTileArea", MIN_TILE_AREA);
        json.addProperty("chartWidth", CHART_WIDTH);
        json.addProperty("chartHeight", TREEMAP_HEIGHT);

        var strings = new JsonArray();
        stringIndex.keySet().forEach(strings::add);
        json.add("s", strings);
        return json;
    }

    /** Amounts of the embedded data in hundredths, as the calculation keeps them. */
    private static long amount(BigDecimal value)
    {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }


    /** For tests: the settings that the page may name. */
    static Set<String> namedSettings()
    {
        return Set.of(KEY_SETTING, CONFIRMATION);
    }
}
