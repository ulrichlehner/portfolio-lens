# Proposal

## Why

Some funds report more than 100% in identified holdings and balance it with negative cash and
currency rows without an identifier, for example a negative "USD CASH" row and small negative
"INR/USD" forward rows. Lens nets the rows without an identifier and rejects the whole fund when
the net is negative. The fund then stays unresolved, although its holdings are known and the
negative net is ordinary short-term cash, not leverage.

## What Changes

- A composition whose rows without an identifier net to a negative amount of at most 10% of the
  fund's NAV becomes usable. Identified rows keep their weights; their sum may exceed 100% by
  exactly the negative net. The rows without an identifier become one netted negative amount, the
  fund's net cash, which is never expanded or classified by name. A non-fatal diagnostic records it.
- A negative net below -10% stays unusable, with its own reason and status text: negative net cash
  beyond the limit, likely leverage. Identified rows with negative weights (short positions) and
  totals above 100% beyond the rounding tolerance stay unusable as before.
- The look-through adds a new contribution category for the net cash of funds. A fund's
  contributions still sum to its value, and net cash never becomes a company.
- The page shows companies at their real values and the net cash of funds as its own line in the
  breakdown table, the summary and the data status. Treemap, bars and coverage bar never show a
  negative segment or more than 100%; a short note under the treemap and the coverage bar names
  the net cash that is not drawn.
- After the upgrade, a fund rejected only by the old rule is fetched and evaluated again on the
  next run, without waiting for the 24-hour fund cadence.

Out of scope:

- Classifying rows without an identifier by their label, or splitting net cash by currency.
- Accepting short positions with an identifier, leveraged or derivative exposure, or totals
  above 100%.
- Changing the denominator: shares stay relative to the portfolio's securities value.
- A per-fund setting for the net cash limit.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `fund-compositions`: validation accepts a bounded negative net of rows without an identifier as
  net cash, rejects a larger one with its own reason, and re-evaluates compositions rejected by
  the old rule after an upgrade.
- `portfolio-look-through`: a new contribution category for the net cash of funds that keeps the
  holding reconciled.
- `breakdown-view`: net cash lines in table, summary and data status; no negative or overfull
  chart segments; a note under treemap and coverage bar.

## Impact

- `core`: `CompositionValidator`, `CompositionValidationResult`, `LookThroughEngine`,
  `LookThroughContribution`.
- `page`: `LookThroughViewPresenter`, `LookThroughDataStatus`, `PageWriter`, `Texts`.
- `job`: `RunState` and `LensRun` for the one-time re-evaluation after the upgrade.
- Tests with synthetic compositions only; README section on fund cash.
