# RetroFlow implementation plan

## Current findings

- `RetroflowBusinessRulesTest` calls `TeamService` and `RetrospectiveService`, but neither service class exists, so the test cannot compile.
- The test covers three rules: closed retrospectives reject new feedback, a team cannot have two open retrospectives, and completed action items cannot be uncompleted.
- The test signatures omit inputs required by the matrix/decisions: retrospective creation has no caller identity or date; closing has no caller identity; completing an action item has no caller identity. Any implementation must reconcile these contract differences without silently bypassing current-membership checks or required date validation.
- `test-matrix.md` and `decisions.md` appear aligned on the domain rules reviewed. The separate `ActionItem` implementation is consistent with the decision that action items are modeled separately despite their conceptual description as feedback items.
- Existing entity, repository, and controller files have uncommitted changes. Preserve and integrate those changes when implementation is authorized.

## Plan

1. Define service operations that cover each operation in `test-matrix.md`, with shared validation for names, content, membership, lifecycle status, soft-deleted resources, and enum/filter values. Throw `RetroflowException` with clear messages for invalid operations and missing-resource exceptions that can be mapped to HTTP 404.
2. Resolve the test/API contract mismatch for caller identity and retrospective date. Keep the tests unchanged; ensure any test-compatible entry point does not weaken the production rule that a member identity and a valid UTC ISO-8601 date are required.
3. Implement team service rules: normalized and case-insensitive team lookup/uniqueness, member validation and uniqueness, member-list changes, minimum membership, and cascading soft deletion.
4. Implement retrospective service rules: title/date/status validation, global case-insensitive title uniqueness, transactional single-open-retrospective enforcement with the required table-level lock, close/reopen behavior, scoped retrieval, and date-descending/ID-ascending ordering.
5. Implement feedback and action-item service rules: parent/team membership checks, open/closed restrictions, separate action-item handling, field restrictions, completion transitions, soft deletion, retrieval/filtering, and required ordering.
6. Add the persistence/query support needed by the services and ensure `IsDeleted` is excluded from API representations. Add or use the API error mapping so invalid operations produce HTTP 400 and missing resources HTTP 404 with an `errors` array.
7. Run the targeted `RetroflowBusinessRulesTest`, then the full test suite and build. Do not modify or skip tests.

## Decision/test-matrix conflicts to resolve

- The test's `createRetrospective(teamId, title)` omits both the current member and the required date; the matrix and decisions require a current member and a non-null UTC ISO-8601 date.
- The test's `closeRetrospective(id)` omits the current member required by the matrix and decisions.
- The test's `completeActionItem(id)` omits the current member required by the matrix and decisions.

No direct disagreement between the reviewed `decisions.md` rules and `test-matrix.md` rules was identified; the conflicts above are between the required service-test signatures and those rules.
