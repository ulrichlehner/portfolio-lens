# fund-overlap Specification

## Purpose
Measures how much the decomposed funds of a portfolio duplicate each other, based on the companies they share
and their weights in each fund.

## Requirements

### Requirement: Pairwise overlap
For every pair of decomposed funds, the system SHALL compute the overlap as the sum, over companies held by both, of the smaller of the two fund weights, and SHALL report the number of shared companies. Funds that are not decomposed SHALL be excluded and listed as such.

#### Scenario: Two funds share companies
- **WHEN** fund A holds company X at 5 percent and Y at 2 percent, and fund B holds X at 3 percent and Y at 4 percent
- **THEN** the overlap of A and B is 5 percent with 2 shared companies.

#### Scenario: Fund without composition
- **WHEN** one held fund has no usable composition
- **THEN** it is absent from the overlap and named as not decomposed.

### Requirement: Shared companies
For each pair, the system SHALL provide the shared companies ordered by their overlap contribution.

#### Scenario: Largest shared company
- **WHEN** company X contributes the most to the overlap of A and B
- **THEN** X is listed first for that pair.
