package io.portfoliolens.page;

/**
 * English texts of the Breakdown, ported from the strings the user wrote for the look-through view
 * of the PP development fork. Patterns use {@link java.text.MessageFormat} placeholders.
 */
public final class Texts
{
    private Texts()
    {
    }

    public static final String LabelBreakdown = "Breakdown";

    public static final String LookThroughAttributeSourceIsinPrefix = "ISIN prefix";

    public static final String LookThroughAttributeSourceProvider = "provider profile";

    public static final String LookThroughAttributeSourceRegister = "legal entity register";

    public static final String LookThroughBannerCheckKey = "Check API key...";

    public static final String LookThroughBannerNoKey = "Funds and directly held shares are broken down by company with an Eulerpool API key.";

    public static final String LookThroughBannerSetUpKey = "Set up API key...";

    public static final String LookThroughBannerSuspended = "Eulerpool rejected the API key or its permission. Funds are not broken down until this is fixed.";

    public static final String LookThroughBreakdownBy = "Breakdown by";

    public static final String LookThroughCategoryCashOrOther = "Cash or other assets";

    public static final String LookThroughCategoryNetCash = "Net cash of funds";

    public static final String LookThroughNetCashTooltip = "Negative cash balances that funds report as rows without identifier. Companies are shown at the funds' real weights; this amount reconciles them with the securities value.";

    public static final String LookThroughNetCashNotShown = "Not shown: net cash of funds {0} ({1})";

    public static final String LookThroughStatusNetCash = "Includes net cash {0} of the fund (rows without identifier)";

    public static final String LookThroughStatusRejectedNetCash = "Rejected: negative net cash beyond {0}";

    public static final String LookThroughStatusExplainNetCash = "The provider returned a composition whose rows without identifier net to a negative cash balance beyond {0} of the fund, which likely means leverage. The fund stays unresolved.";

    public static final String LookThroughCategoryCashOrOtherTooltip = "Cash, bonds, derivatives and other positions in funds that are not company shares.";

    public static final String LookThroughCategoryUngroupedEquity = "Ungrouped equity";

    public static final String LookThroughCategoryUngroupedEquityTooltip = "Equity positions in funds that could not be assigned to a company.";

    public static final String LookThroughCategoryUnresolved = "Not yet decomposed";

    public static final String LookThroughCategoryUnresolvedTooltip = "Positions in funds that could not be assigned to a company: cash, funds within funds, bonds or securities without a classification.";

    public static final String LookThroughCategoryUnverified = "Unverified provider positions";

    public static final String LookThroughCategoryUnverifiedTooltip = "Positions reported by the data provider whose weights could not be verified.";

    public static final String LookThroughChartBarColor = "Bar color:";

    public static final String LookThroughChartEmpty = "No company exposure to show yet.";

    public static final String LookThroughChartHeldDirectly = "Held directly";

    public static final String LookThroughChartOverlap = "Overlap";

    public static final String LookThroughChartThroughFunds = "Through funds";

    public static final String LookThroughChartTileColor = "Tile color:";

    public static final String LookThroughChartTopCompanies = "Top 20";

    public static final String LookThroughChartTreemap = "Treemap";

    public static final String LookThroughColumnCompany = "Company / instrument";

    public static final String LookThroughColumnDirect = "Direct";

    public static final String LookThroughColumnGrouped = "{0} / company";

    public static final String LookThroughColumnIndirect = "Indirect";

    public static final String LookThroughColumnRank = "#";

    public static final String LookThroughColumnShare = "Share of selected value";

    public static final String LookThroughColumnTotal = "Total";

    public static final String LookThroughCompanyDataComplete = "Company data: {0} of {1} loaded";

    public static final String LookThroughCompanyDataLoading = "Company data: {0} of {1} loaded, loading more";

    public static final String LookThroughCompanyDataNoAccess = "Company data: {0} of {1} loaded, access denied by the provider";

    public static final String LookThroughCompanyDataNoKey = "Company data: {0} of {1} loaded, no API key configured";

    public static final String LookThroughCompanyDataPaused = "Company data: {0} of {1} loaded, requests paused until {2}";

    public static final String LookThroughCompanyDataPausedOpen = "Company data: {0} of {1} loaded, requests paused";

    public static final String LookThroughCompanyDataUnavailable = "Company data: {0} of {1} loaded, provider not reachable, next attempt at {2}";

    public static final String LookThroughCoverageCaption = "Coverage:";

    public static final String LookThroughCoverageCompanies = "Companies";

    public static final String LookThroughCoverageUnavailable = "Coverage cannot be calculated because the securities value is incomplete.";

    public static final String LookThroughDetailCountry = "Country: {0} (source: {1})";

    public static final String LookThroughDetailRevenue = "{0} of revenue, period ending {1}";

    public static final String LookThroughDetailRevenueFallback = "{0} of revenue, allocated to the headquarters country as fallback (no revenue data)";

    public static final String LookThroughDetailRevenueScaled = "{0} of revenue, period ending {1}; reported entries add up to {2}, scaled to 100 %";

    public static final String LookThroughDetailUnnamedInstrument = "Unnamed instrument";

    public static final String LookThroughGroupCompanies = "{0} companies";

    public static final String LookThroughGroupNoRevenue = "No revenue data ({0})";

    public static final String LookThroughGroupPending = "Pending ({0})";

    public static final String LookThroughGroupUnknown = "Unknown ({0})";

    public static final String LookThroughGroupingCompany = "Company";

    public static final String LookThroughGroupingCountry = "Country (headquarters)";

    public static final String LookThroughGroupingCurrency = "Currency";

    public static final String LookThroughGroupingIndustry = "Industry";

    public static final String LookThroughGroupingMarket = "Market classification";

    public static final String LookThroughGroupingRegion = "Region";

    public static final String LookThroughGroupingRevenueRegion = "Revenue region";

    public static final String LookThroughGroupingSector = "Sector";

    public static final String LookThroughLabelCalculating = "Breakdown is being calculated...";

    public static final String LookThroughLabelComplete = "Complete coverage";

    public static final String LookThroughLabelDenominator = "Denominator: current value of held securities, excluding account cash ({0})";

    public static final String LookThroughLabelEmptyPortfolio = "No current holdings in this portfolio file. The breakdown covers every instrument that Portfolio Performance reports as held.";

    public static final String LookThroughLabelNoDenominator = "No denominator: share percentages are withheld because the held securities value is incomplete or zero.";

    public static final String LookThroughLabelPartial = "Partial coverage";

    public static final String LookThroughLabelScope = "All portfolio positions with holdings as of {0}. Account cash is excluded.";

    public static final String LookThroughLabelUnverifiedNotice = "The underlying issuers are shown from provider data, but the allocation semantics of the fund weights are not validated. Coverage stays partial.";

    public static final String LookThroughNoticeConcentration = "{0} accounts for {1} of the portfolio value.";

    public static final String LookThroughNoticeExcluded = "Not included: {0}";

    public static final String LookThroughOverlapExcluded = "Not decomposed: {0}";

    public static final String LookThroughOverlapLargestShared = "Largest shared companies";

    public static final String LookThroughOverlapNeedTwoFunds = "At least two decomposed funds are needed to show the overlap.";

    public static final String LookThroughOverlapShared = "Shared companies: {0}";

    public static final String LookThroughOverlapSharedCount = "{0} shared";

    public static final String LookThroughReasonAmbiguousClassification = "Ambiguous instrument type";

    public static final String LookThroughReasonAmbiguousCompany = "Ambiguous company grouping";

    public static final String LookThroughReasonAmbiguousIdentity = "Ambiguous identifier";

    public static final String LookThroughReasonCompositionShortfall = "Portion not covered by the supplied weights";

    public static final String LookThroughReasonLookupFailed = "Lookup failed, will retry";

    public static final String LookThroughReasonLookupPending = "Not yet looked up";

    public static final String LookThroughReasonMissingCompany = "No company grouping";

    public static final String LookThroughReasonMissingComposition = "No composition available for this fund";

    public static final String LookThroughReasonMissingConversion = "Missing currency conversion";

    public static final String LookThroughReasonMissingIdentifiers = "No identifier";

    public static final String LookThroughReasonMissingIdentity = "No classification";

    public static final String LookThroughReasonMissingResolution = "No classification evidence";

    public static final String LookThroughReasonMissingValuation = "Missing valuation";

    public static final String LookThroughReasonNameNotCompany = "Name indicates fund, cash or derivative";

    public static final String LookThroughReasonNoClassificationFound = "No classification found";

    public static final String LookThroughReasonProviderWeightUnverified = "Provider weight, not validated";

    public static final String LookThroughReasonTraversalStopped = "Decomposition stopped: cycle, depth limit or inconsistent identity";

    public static final String LookThroughReasonUnknownClassification = "Unknown instrument type";

    public static final String LookThroughReasonUnsupportedNegativePosition = "Unsupported negative position";

    public static final String LookThroughReasonUnusableComposition = "Composition cannot be used (weights or exposure basis)";

    public static final String LookThroughRefreshAction = "Refresh";

    public static final String LookThroughRegionApac = "Asia Pacific";

    public static final String LookThroughRegionEmea = "Europe, Middle East and Africa";

    public static final String LookThroughRegionGreaterChina = "Greater China (China, Hong Kong, Taiwan)";

    public static final String LookThroughRegionLatam = "Latin America";

    public static final String LookThroughRegionOther = "Other or unspecified";

    public static final String LookThroughRevenueCoverage = "Revenue data: {0} by revenue, {1} by headquarters as fallback, {2} without data; reporting periods: {3}.";

    public static final String LookThroughRevenueCoverageNone = "No revenue data is available for the companies.";

    public static final String LookThroughRevenueIncomplete = "{0} companies report revenue splits that do not add up to 100 %. They are scaled to 100 %.";

    public static final String LookThroughSourceAssumed = "assumed, not confirmed";

    public static final String LookThroughSourceDirect = "Direct holding";

    public static final String LookThroughSourceIndirect = "via {0} (origin: {1})";

    public static final String LookThroughStatusAgeFormatter = "{0,choice,0#less than an hour|1#one hour|1<{0,number} hours}";

    public static final String LookThroughStatusAssumedCompanies = "{0,choice,1#1 position|1<{0,number,integer} positions} assumed to be companies ({1})";

    public static final String LookThroughStatusAttemptAuthenticationFailed = "Authentication failed. Reconfigure the provider key before retrying.";

    public static final String LookThroughStatusAttemptCancelled = "The last retrieval attempt was cancelled.";

    public static final String LookThroughStatusAttemptEntitlementFailed = "Entitlement failed. Confirm the provider permission before retrying.";

    public static final String LookThroughStatusAttemptFailed = "The last retrieval attempt failed.";

    public static final String LookThroughStatusAttemptMalformedResponse = "The last response was not captured as a consistent composition.";

    public static final String LookThroughStatusAttemptRateLimited = "The provider rate limit was reached during the last retrieval attempt.";

    public static final String LookThroughStatusAttemptReceived = "Source data was retrieved from the provider. Retrieval itself is not a statement about consistency or completeness.";

    public static final String LookThroughStatusAttemptUnavailable = "The provider has no composition data for this source right now.";

    public static final String LookThroughStatusAttemptUnsupported = "The provider does not support composition data for this source.";

    public static final String LookThroughStatusChecking = "Not retrieved yet";

    public static final String LookThroughStatusColumnFund = "Instrument";

    public static final String LookThroughStatusColumnRetrieved = "Retrieved";

    public static final String LookThroughStatusColumnShare = "Broken down";

    public static final String LookThroughStatusColumnStatus = "Status";

    public static final String LookThroughStatusExplainInvalidWeight = "The provider returned a composition with missing or invalid weights. The fund stays unresolved.";

    public static final String LookThroughStatusExplainMalformed = "The provider response could not be read as a composition. The fund stays unresolved.";

    public static final String LookThroughStatusExplainNegativeWeight = "The provider returned a composition with negative weights. The fund stays unresolved.";

    public static final String LookThroughStatusExplainNotConfigured = "Without an Eulerpool API key this instrument is not broken down.";

    public static final String LookThroughStatusExplainRejected = "The provider returned a composition that failed validation. The fund stays unresolved.";

    public static final String LookThroughStatusExplainTotalAboveOne = "The provider returned a composition whose weights add up to more than 100 %. The fund stays unresolved.";

    public static final String LookThroughStatusFailed = "Refresh failed";

    public static final String LookThroughStatusFetching = "Fetching...";

    public static final String LookThroughStatusFresh = "Retrieval is fresh: {0} ago";

    public static final String LookThroughStatusHoldingsDate = "Holdings effective as of {0}";

    public static final String LookThroughStatusHoldingsUnavailable = "The holdings effective date is not available.";

    public static final String LookThroughStatusKeyRejected = "Key rejected";

    public static final String LookThroughStatusLimitationDiskFailure = "Writing composition data to the Lens cache directory failed. Composition data was used for this run only; the next run retrieves it again.";

    public static final String LookThroughStatusLimitationSessionOnly = "Provider data is neither requested nor stored until local storage is confirmed with LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true.";

    public static final String LookThroughStatusMalformed = "Malformed provider response";

    public static final String LookThroughStatusModeDiskFailure = "Session only: writing composition data to disk failed";

    public static final String LookThroughStatusNettedIdentifierlessRows = "Cash and currency rows without identifier were netted; the remainder stays unresolved.";

    public static final String LookThroughStatusNoAccess = "No access";

    public static final String LookThroughStatusNoAttempt = "No retrieval attempt has been made yet.";

    public static final String LookThroughStatusNoComposition = "No composition available";

    public static final String LookThroughStatusNotConfigured = "No API key";

    public static final String LookThroughStatusOk = "OK";

    public static final String LookThroughStatusPaneEmpty = "No composition data status is available yet.";

    public static final String LookThroughStatusPaneTitle = "Data status";

    public static final String LookThroughStatusPendingRefresh = "(pending for a later run)";

    public static final String LookThroughStatusProviderAccount = "Provider {0}, local account {1}";

    public static final String LookThroughStatusProviderRefreshedDate = "Provider source refreshed {0}";

    public static final String LookThroughStatusProviderRefreshedUnavailable = "The provider did not report when its source was last refreshed.";

    public static final String LookThroughStatusRateLimited = "Limit reached";

    public static final String LookThroughStatusRateLimitedRetry = "Limit reached, retry {0}";

    public static final String LookThroughStatusRejected = "Rejected by validation";

    public static final String LookThroughStatusRejectedInvalidWeight = "Rejected: missing or invalid weights";

    public static final String LookThroughStatusRejectedNegativeWeight = "Rejected: negative weights";

    public static final String LookThroughStatusRejectedTotalAboveOne = "Rejected: total above 100 %";

    public static final String LookThroughStatusRetainedAfterFailure = "Refresh failed, older data used";

    public static final String LookThroughStatusRetrievalDate = "Retrieved {0}";

    public static final String LookThroughStatusRetryHint = "Next retry is possible after {0}";

    public static final String LookThroughStatusRowCollapse = "Hide data status";

    public static final String LookThroughStatusRowExpand = "Show data status";

    public static final String LookThroughStatusRowFailed = "{0} failed";

    public static final String LookThroughStatusRowFailedRetry = "{0} failed, next attempt {1}";

    public static final String LookThroughStatusRowRunning = "Lookups: {0} active, {1} waiting, {2} of {3} answered";

    public static final String LookThroughStatusRowSettled = "Funds with data: {0}, without composition: {1}";

    public static final String LookThroughStatusRowTooltip = "Show or hide the data status";

    public static final String LookThroughStatusSourceDataComplete = "The provider declares the composition complete.";

    public static final String LookThroughStatusSourceDataPartial = "The provider declares the composition partial.";

    public static final String LookThroughStatusSourceDataUnknown = "The provider does not declare a composition completeness.";

    public static final String LookThroughStatusSourceDenominatorReportedHoldings = "Composition rows are allocated in percent of the reported holdings.";

    public static final String LookThroughStatusSourceDenominatorTotalFundNav = "Composition rows are allocated in percent of the total fund net asset value.";

    public static final String LookThroughStatusSourceDenominatorUnknown = "The composition denominator is not specified.";

    public static final String LookThroughStatusStale = "Retrieval is older than 24 hours: {0} ago";

    public static final String LookThroughStopRefresh = "Stop refresh";

    public static final String LookThroughSummaryAsOf = "As of {0}";

    public static final String LookThroughSummaryConcentration = "Concentration";

    public static final String LookThroughSummaryConcentrationTooltip = "The portfolio is about as concentrated as {0} equally sized companies. The shares of the {1} recognized companies are squared, added and the reciprocal of that sum is taken. Example: 1 / (0.3333^2 + 0.3333^2 + 0.3333^2) rounds to 3. The same 100 percent split as 50, 25 and 25 percent gives 1 / (0.5^2 + 0.25^2 + 0.25^2) about 2.67, so the concentration increased. A smaller value means higher concentration, a value near the total means evenly spread. The basis is the recognized companies, the unresolved remainder does not count.";

    public static final String LookThroughSummaryConcentrationValue = "~{0} of {1}";

    public static final String LookThroughSummaryFunds = "Funds broken down";

    public static final String LookThroughSummaryLargest = "Largest company";

    public static final String LookThroughSummaryLargestTooltip = "The largest single company and its share of the portfolio value.";

    public static final String LookThroughSummaryOf = "{0} of {1}";

    public static final String LookThroughSummaryResolved = "Resolved";

    public static final String LookThroughSummaryTopTen = "Top 10 companies";

    public static final String LookThroughSummaryValue = "Securities value";

    public static final String LookThroughSummaryValueIncomplete = "{0} (incomplete)";

    public static final String PrefEulerpoolPersistenceModePersistent = "Provider data is stored in the Lens cache directory.";

    public static final String PrefEulerpoolPersistenceModeSessionOnly = "Provider data is not stored.";

    public static final String PrefEulerpoolPersistenceReasonPolicyProhibited = "Provider data is not used because the provider does not permit retention.";

    public static final String PrefEulerpoolPersistenceReasonPolicyUnverified = "Provider data is not used until local storage is confirmed (LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true).";

    public static final String PrefEulerpoolPersistenceReasonUserOptOut = "Provider data is not used because retention is turned off.";
}
