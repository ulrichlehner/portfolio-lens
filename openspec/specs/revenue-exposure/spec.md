# revenue-exposure Specification

## Purpose
Allocates company exposure to countries and regions by each company's reported revenue by geography, so the
Breakdown can show economic exposure in addition to headquarters countries.

## Requirements

### Requirement: Revenue split per company
The system SHALL retrieve a company's revenue by geography from the provider and use the latest reported period. Each entry SHALL be normalized to a country, a known region or "Other or unspecified". Percentages SHALL be scaled to 100 percent when the reported entries do not sum to it, and the original sum SHALL be kept for display.

#### Scenario: Mixed members
- **WHEN** a company reports 60 percent United States, 25 percent Europe and 15 percent rest of world
- **THEN** its split is United States 60, Europe region 25 and Other or unspecified 15 percent.

#### Scenario: Incomplete report
- **WHEN** the reported percentages sum to 90 percent
- **THEN** the split is scaled to 100 percent and the data status names the company as incomplete.

### Requirement: Exposure allocation by revenue
A company's total exposure SHALL be allocated to the members of its revenue split proportionally. Companies without revenue data SHALL be allocated entirely to their headquarters country as a marked fallback, and remain unallocated only when that country is unknown. The allocated and unallocated amounts SHALL add up to the company exposure.

#### Scenario: Allocation
- **WHEN** a company with exposure 1000 has a split of 60 percent United States and 40 percent Europe
- **THEN** 600 is allocated to United States and 400 to Europe.

### Requirement: Bounded retrieval and caching
Revenue requests SHALL be scheduled in descending order of company value within the provider request budget. Splits SHALL be cached under the account's persistence policy and refreshed after 90 days.

#### Scenario: Restart within 90 days
- **WHEN** PP restarts and all current companies have cached splits younger than 90 days
- **THEN** the revenue grouping is available without provider requests.
