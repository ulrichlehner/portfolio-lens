# Tasks

Branch `feature/net-cash-in-fund-compositions` from `origin/main`.

## 1. Validation

- [x] 1.1 `CompositionValidator`: `NET_CASH_LIMIT` (-10% of NAV) and `RULES_VERSION` 2; a negative identifierless net within the limit gives a usable result with identified rows at their weights, `netCash()` and the non-fatal `NET_CASH` diagnostic; below the limit the fatal `NET_CASH_BEYOND_LIMIT`; rounding excess removed from identified rows so that rows plus net cash reconcile to one. Tests in `CompositionValidatorTest`: 103% / -3% usable, -12% rejected with the new reason, negative identified row rejected as before, total above one still rejected. Evidence: `CompositionValidatorTest` (31 tests) covers 103% identified rows with -3% net cash (usable, weights unchanged, net cash -0.030, zero shortfall), net cash at exactly -10% (usable), -12% (rejected with `NET_CASH_BEYOND_LIMIT`), a negative identified row (rejected as before), an overfull total, and a rounding excess with net cash that reconciles to one.

## 2. Look-through

- [x] 2.1 `LookThroughContribution`: category `NET_CASH`, reason `NET_CASH_OF_FUNDS`; `LookThroughEngine` adds the negative net cash contribution with a path edge. Test: contributions of the holding sum to its value, companies get 103%, no company from a cash row. Evidence: `LookThroughEngineTest.testNetCashOfFundKeepsTheHoldingReconciled` (a holding of 10,000: companies 10,300, net cash -300, sum 10,000, no company from a cash row) and `testNetCashBeyondTheLimitLeavesTheFundUnresolved`.

## 3. Page

- [x] 3.1 Presenter: net cash category row and summary item when nonzero; coverage widths never exceed the bar; data status note on the fund row and the new rejection text. Evidence: `LookThroughDataStatusTest.testNettedRowsWithoutIdentifierAreNamedInTheTooltip` (OK row with the net cash note; beyond the limit `REJECTED_NET_CASH_BEYOND_LIMIT` with the leverage explanation).
- [x] 3.2 `PageWriter`: notes under treemap and coverage bar. Test: page with net cash shows the table row, summary item, note, no negative tile or bar width, and the data status note; a page without net cash shows none of them. Evidence: `NetCashRunTest` runs the job on a synthetic portfolio: table row and summary item "Net cash of funds", a note under treemap and coverage bar ("Not shown: net cash of funds EUR -30.60 (-1.49%)"), "includes net cash -3.00%" in the fund row, no rect with a negative size, coverage widths within the bar, no cash row label on the page; without net cash none of these appear. The page script and the merged tile are unchanged; `PageSmokeTest` and `TreemapParityTest` pass. Not checked in a browser.

## 4. Rollout

- [x] 4.1 `RunState.validationRules`, the fingerprint includes `RULES_VERSION`, and attempts without a usable composition are not restored from an older rules version. Test: a run with the old rule state rejects the fund; the next run on unchanged input re-fetches only that fund and uses it. Evidence: `NetCashRunTest.testFundRejectedUnderOlderRulesIsEvaluatedAgainAfterTheUpgrade` (state without `validationRules` and an older fingerprint: the next run requests only the rejected fund once and uses it, the run after requests nothing) and `testWithoutTheRulesChangeARejectedFundWaitsForItsCadence`.
- [x] 4.2 README: fund net cash, the limit, and the one-time re-evaluation after the upgrade. Evidence: README section in "What it does".

## 5. Verification and delivery

- [x] 5.1 `scripts/dev.sh test` passes; `scripts/openspec validate net-cash-in-fund-compositions --strict` and `scripts/openspec validate --specs --strict` pass. Evidence: 471 tests, 0 failures; both validations report valid (9 specs passed).
- [x] 5.2 Privacy scan of the diff (no real ISINs, names, amounts, paths, tokens); commit with Conventional Commits; push the branch and open a pull request into `main`; do not merge. Evidence: the scan of the staged diff found no real values, ISINs, home paths or tokens; pull request https://github.com/ulrichlehner/portfolio-lens/pull/4.
- [x] 5.3 Merge and archive. Evidence: pull request 4 was merged into `main` on 2026-11-05 (merge commit 6b60a82, CI "Maven build" passed); the change was archived and its deltas synced into the main specs, and `scripts/openspec validate --specs --strict` passes.
