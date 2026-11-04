package io.portfoliolens.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.portfoliolens.core.InstrumentEvidence.AssetType;
import io.portfoliolens.core.InstrumentEvidence.Identifier;

/**
 * UNGROUPED means identified, classified equity (including depositary receipts)
 * without verified company grouping. UNRESOLVED means identity or classification
 * is missing or conflicting. Neither is verified company exposure. FUND is a
 * traversal candidate, not cash/other or an already resolved exposure: composition
 * usability, exposure basis, cycles and depth must still be checked by the engine.
 */
public record InstrumentResolution(CompositionInstrument source, State state, Reason reason,
                Optional<Identifier> instrument, Optional<AssetType> classification, Optional<Identifier> company,
                List<InstrumentEvidence> evidence, LookupStatus lookupStatus)
{
    public enum State
    {
        GROUPED_EQUITY, UNGROUPED_EQUITY, CASH_OR_OTHER, FUND, UNRESOLVED
    }

    public enum Reason
    {
        VERIFIED, MISSING_IDENTIFIERS, MISSING_IDENTITY, AMBIGUOUS_IDENTITY, UNKNOWN_CLASSIFICATION,
        AMBIGUOUS_CLASSIFICATION, MISSING_COMPANY, AMBIGUOUS_COMPANY,

        /**
         * Not verified: a constituent with an ISIN for which the classification
         * lookup found nothing, and whose name does not indicate a fund, cash,
         * a bond or a derivative, is assumed to be a listed company share.
         */
        ASSUMED_COMPANY
    }

    public enum LookupStatus
    {
        NOT_NEEDED, NO_IDENTIFIERS, BUDGET_EXHAUSTED, ATTEMPTED, FAILED,
        /** The provider asked to pause, or recent failures put lookups on hold. */
        BACKOFF,
        /** The lookup cap of one refresh or the run time limit was reached. */
        LOOKUP_LIMIT,
        /** No stored answer exists yet; a later run looks it up. */
        NOT_RETRIEVED
    }

    /** The same resolution with another lookup status, to name why no lookup was made. */
    public InstrumentResolution withLookupStatus(LookupStatus status)
    {
        return new InstrumentResolution(source, state, reason, instrument, classification, company, evidence, status);
    }

    public InstrumentResolution
    {
        Objects.requireNonNull(source);
        Objects.requireNonNull(state);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(instrument);
        Objects.requireNonNull(classification);
        Objects.requireNonNull(company);
        evidence = List.copyOf(evidence);
        Objects.requireNonNull(lookupStatus);
    }
}
