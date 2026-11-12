package io.portfoliolens.page;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.portfoliolens.core.InstrumentEvidence.Identifier;
import io.portfoliolens.core.InstrumentResolution;
import io.portfoliolens.core.LookThroughContribution.Category;
import io.portfoliolens.core.LookThroughResult;
import io.portfoliolens.core.SecuritiesValuation.Position;
import io.portfoliolens.page.LookThroughDataStatus.FundStatus;
import io.portfoliolens.page.LookThroughViewPresenter.Model;

/**
 * Rows of the fund table: one per held fund with its value, its share of the
 * securities value, the number of companies it contributes and the part that
 * was looked through. A held fund is a security that the look-through result
 * classifies as a fund. A listed security that is not one is still unclassified.
 */
public final class FundRows
{
    private static final String NOT_AVAILABLE = LookThroughDataStatus.NOT_AVAILABLE;

    public record Row(FundStatus status, Optional<BigDecimal> value, String valueText, String shareText,
                    String companiesText, String lookedThroughText)
    {
    }

    private FundRows()
    {
    }

    /** Held funds, largest value first; equal values by label. */
    public static List<Row> funds(LookThroughResult result, Model company, List<FundStatus> rows)
    {
        var values = new HashMap<Map<String, String>, Set<Position>>();
        var companies = new HashMap<Map<String, String>, Set<Identifier>>();
        for (var contribution : result.contributions())
        {
            if (!isFund(contribution.originResolution()))
                continue;
            var key = contribution.origin().security().asCompositionInstrument().identifiers();
            values.computeIfAbsent(key, ignored -> new HashSet<>()).add(contribution.origin());
            var identities = companies.computeIfAbsent(key, ignored -> new HashSet<>());
            if (!contribution.isDirect() && contribution.category() == Category.COMPANY)
                contribution.resolution().flatMap(InstrumentResolution::company).ifPresent(identities::add);
        }

        var currency = company.currencyCode();
        var denominator = company.denominator().map(value -> BigDecimal.valueOf(value.getAmount()))
                        .filter(value -> value.signum() > 0);
        return rows.stream().filter(row -> values.containsKey(row.reference().fund().identifiers()))
                        .map(row -> {
                            var key = row.reference().fund().identifiers();
                            var value = values.get(key).stream()
                                            .map(position -> BigDecimal.valueOf(position.value().getAmount()))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            var share = denominator.map(amount -> value.divide(amount, 6, RoundingMode.HALF_UP));
                            var usable = row.usableData();
                            return new Row(row, Optional.of(value), plain(value, currency),
                                            share.map(fraction -> Values.Percent2.format(fraction.doubleValue()))
                                                            .orElse(NOT_AVAILABLE),
                                            usable ? String.valueOf(companies.get(key).size()) : NOT_AVAILABLE,
                                            LookThroughDataStatus.shareText(row, company.decomposedShares()));
                        })
                        .sorted(Comparator.comparing((Row row) -> row.value().orElseThrow()).reversed()
                                        .thenComparing(row -> row.status().fundLabel()))
                        .toList();
    }

    /**
     * Listed securities that are not held funds: their classification is still
     * unknown, so they may turn out to be funds.
     */
    public static List<FundStatus> unclassified(LookThroughResult result, List<FundStatus> rows)
    {
        var funds = new HashSet<Map<String, String>>();
        for (var contribution : result.contributions())
            if (isFund(contribution.originResolution()))
                funds.add(contribution.origin().security().asCompositionInstrument().identifiers());
        return rows.stream().filter(row -> !funds.contains(row.reference().fund().identifiers())).toList();
    }

    private static boolean isFund(Optional<InstrumentResolution> resolution)
    {
        return resolution.filter(value -> value.state() == InstrumentResolution.State.FUND).isPresent();
    }

    /** An amount of a table cell: the currency stands in the column head. */
    private static String plain(BigDecimal value, String currency)
    {
        return LookThroughViewPresenter.formatAmount(value, currency).replace(currency, "").trim();
    }
}
