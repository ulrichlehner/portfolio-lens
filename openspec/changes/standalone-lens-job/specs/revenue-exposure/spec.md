# Spec Delta

## MODIFIED Requirements

### Requirement: Bounded retrieval and caching
Revenue requests SHALL be scheduled in descending order of company value within the provider request budget shared across runs. Splits SHALL be cached in the cache directory under the provider's persistence rules and refreshed after 90 days.

#### Scenario: Restart within 90 days
- **WHEN** a run starts and all current companies have cached splits younger than 90 days
- **THEN** the revenue grouping is available without provider requests.
