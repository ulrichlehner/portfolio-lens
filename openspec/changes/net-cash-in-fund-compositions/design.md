# Design

## Context

`CompositionValidator` nets all rows without a security identifier. A zero or positive net stays
one unresolved row; a negative net adds a fatal `NEGATIVE_WEIGHT` diagnostic without a row index,
and the fund stays unresolved. Rejected compositions are not stored. The refresh coordinator
records the attempt with a failure backoff, and after the backoff the 24-hour cadence of the
attempt applies, so a fund rejected today is asked again only tomorrow. The run state
(`state.json`) keeps these attempts between runs, and a run on unchanged input is skipped
entirely while no provider work is due.

## Goals / Non-Goals

Goals: use the real weights of a fund with a moderately negative cash balance, keep every holding
reconciled to its value, keep the charts free of negative and overfull parts, and re-evaluate
affected funds right after the upgrade.

Non-goals: label-based classification of cash rows, short positions with identifier, leverage,
derivative exposure, a configurable limit.

## Decisions

### D1 Net cash limit

`CompositionValidator.NET_CASH_LIMIT = -0.10` of NAV. A net of rows without an identifier from
the limit up to zero (exclusive) is net cash: the result is usable, its `rows()` contain only the
identified rows with unchanged weights, and `netCash()` holds the net weight and the source index
of the most negative row (for provenance). A non-fatal diagnostic `NET_CASH` is added. A net below
the limit adds the fatal `NET_CASH_BEYOND_LIMIT`. `NEGATIVE_WEIGHT` stays for identified rows with
a negative weight, `TOTAL_EXCEEDS_ONE` for the total of all rows including the net.

Why -10%: ordinary cash and forward balances of physical equity funds are a few percent. A larger
negative balance points to borrowing or derivative exposure, which the physical-holdings model of
Lens does not represent. The limit is a named constant so later evidence can change it.

### D2 Shortfall and rounding

The shortfall stays one minus the total of all rows including the negative net. A rounding excess
within the tolerance is removed from the identified rows proportionally so that identified rows
plus net cash reconcile exactly to one.

### D3 Contribution category

`LookThroughContribution.Category.NET_CASH` with reason `NET_CASH_OF_FUNDS`. The engine adds one
negative contribution per expanded composition with net cash: holding value times the net weight,
with a path edge to the fund's composition. It is not a company, not cash or other assets (which
stays positive), and not unresolved. `LookThroughResult` keeps its reconciliation check: the sum
of all contributions equals the valued subset.

### D4 Page

- Table: a category row "Net cash of funds" with its negative amount and share, only when nonzero,
  with detail rows per fund.
- Summary: an item "Net cash of funds" with amount and share, only when nonzero.
- Coverage bar: net cash is not a segment. Segment widths are scaled by the sum of the positive
  segments' fractions when it exceeds one, so the bar never runs over; shares in legend and
  tooltips stay relative to the securities value. A note under the bar names the net cash.
- Treemap and bars draw companies only, so they never contain negative values. A note under the
  treemap reads "Not shown: net cash of funds EUR -150.00 (-1.00%)".
- Data status: the fund row stays OK and its tooltip says "Includes net cash -3.00% of the fund
  (rows without identifier)". A composition beyond the limit shows "Rejected: negative net cash
  beyond 10%" with an explanation that this likely means leverage.

Shares keep the securities value as denominator, so company shares can add up to more than the
resolved share by exactly the net cash.

### D5 Rollout

`CompositionValidator.RULES_VERSION = 2`, kept in `state.json` as `validationRules` (absent means
1) and included in the input fingerprint, so the first run after the upgrade is not skipped. When
the stored version is older, the run does not restore persisted attempts that ended without a
usable composition (`noComposition` false and a retry time set): those are the funds rejected or
failed under the old rules. Their refresh then requests the composition once and evaluates it with
the new rules. Funds with a stored composition keep their cadence; nothing else is re-fetched.

## Risks / Trade-offs

- A fund with real leverage up to 10% is shown as fully invested in its holdings plus a negative
  cash line. That matches its reported weights; the data status names the net cash.
- Company totals can exceed the resolved share. The summary line for net cash explains the
  difference.

## Migration Plan

No data migration. The first run after the upgrade re-fetches only the compositions that were
rejected or failed before; the README documents it.

## Open Questions

None.
