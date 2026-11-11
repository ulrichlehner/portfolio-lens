package io.portfoliolens.page;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.portfoliolens.page.LookThroughDataStatus.FundStatus;
import io.portfoliolens.page.LookThroughDataStatus.StatusKind;
import io.portfoliolens.page.LookThroughViewPresenter.Model;

/**
 * The data quality warning: whether something needs the reader's attention
 * and the short text of the first problem. Compositions whose weights are not
 * validated are always present and never raise attention by themselves; they
 * are explained in the data quality section instead.
 *
 * @param attention
 *            true when one of the problems exists
 * @param problemText
 *            the first problem in this order: a failed lookup, a rejected
 *            composition, a held fund without usable composition, a security
 *            without valuation, a value that stays unresolved
 */
public record DataQuality(boolean attention, Optional<String> problemText)
{
    public DataQuality
    {
        Objects.requireNonNull(problemText);
    }

    /**
     * @param rows
     *            the listed status rows of the funds; empty when funds are not
     *            looked up, for example without a key or without confirmed
     *            local storage
     * @param company
     *            the company model, which tells how many held funds have no
     *            usable composition, whether a security has no valuation and
     *            whether value stays unresolved
     */
    public static DataQuality of(List<FundStatus> rows, Model company)
    {
        Objects.requireNonNull(rows);
        Objects.requireNonNull(company);

        var failed = rows.stream().filter(row -> isLookupFailure(row.kind())).count();
        if (failed > 0)
            return problem(MessageFormat.format(Texts.LensQualityLookupFailed, failed));

        var rejected = rows.stream().filter(row -> isRejection(row.kind())).count();
        if (rejected > 0)
            return problem(MessageFormat.format(Texts.LensQualityRejected, rejected));

        var summary = company.summary();
        // a fund counts as decomposed when it has any usable composition, unvalidated weights included
        var withoutComposition = summary.heldFunds() - summary.decomposedFunds();
        if (withoutComposition > 0)
            return problem(MessageFormat.format(Texts.LensQualityNoComposition, withoutComposition));

        if (summary.valuationProblem())
            return problem(Texts.LensQualityNoValuation);

        if (summary.unresolvedValue().signum() != 0)
            return problem(MessageFormat.format(Texts.LensQualityUnresolved,
                            summary.unresolvedText().orElse(LookThroughDataStatus.NOT_AVAILABLE)));

        return new DataQuality(false, Optional.empty());
    }

    private static DataQuality problem(String text)
    {
        return new DataQuality(true, Optional.of(text));
    }

    private static boolean isLookupFailure(StatusKind kind)
    {
        return switch (kind)
        {
            case FAILED, MALFORMED, RETAINED_AFTER_FAILURE, KEY_REJECTED, NO_ACCESS -> true;
            default -> false;
        };
    }

    private static boolean isRejection(StatusKind kind)
    {
        return switch (kind)
        {
            case REJECTED_NEGATIVE_WEIGHT, REJECTED_NET_CASH_BEYOND_LIMIT, REJECTED_TOTAL_ABOVE_ONE,
                            REJECTED_INVALID_WEIGHT, REJECTED -> true;
            default -> false;
        };
    }
}
