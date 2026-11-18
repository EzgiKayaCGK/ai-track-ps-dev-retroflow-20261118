# RetroFlow: Domain and Requirements Questions

This document explains the domain and records requirements that may need clarification. It is a review draft: edit or remove items that the team agrees are already clear.

## Domain overview

RetroFlow records a team's retrospectives and the feedback gathered during them. A **team** has a name and a list of member names. Each **retrospective** belongs to one team and has a title, date, and `OPEN` or `CLOSED` status. A team may have several retrospectives, but at most one can be open at a time; once closed, a retrospective stays closed.

A **feedback item** belongs to a retrospective and records text, a type (`WENT_WELL`, `NEEDS_IMPROVEMENT`, or `ACTION_ITEM`), and the submitting member's name. Conceptually, an action item is a special kind of feedback item; in the implementation, action items are modeled as a separate entity with content, priority, and completion status. Items can be added to open retrospectives; items in closed retrospectives cannot be changed or deleted. Completed action items cannot be marked incomplete.

**Decision:** Action items are implemented as a separate entity. The brief still describes them conceptually as a special kind of feedback item.

## Ambiguities and missing details

Each section quotes the relevant sentence from the brief. **Inference** labels interpretations that seem plausible but are not specified requirements.

### Teams and membership

> “A **team** has a name and a list of members (stored as names, no authentication required)”

- Are team names required to be unique? Are member names unique within a team, or can two people share a name?
    - decision : Team names are unique case-insensitively. Member names must also be unique.
- What counts as a valid name: can it be blank or whitespace-only, and are there length limits?
    - decision : Names must be non-empty, non-whitespace UTF-8 strings with a maximum length of 256 characters. Team names are trimmed of leading and trailing whitespace; member names must not have leading or trailing whitespace.
- Can teams or their member lists be updated or deleted? The brief only specifies creating and retrieving teams.
    - decision : Yes, a team can be deleted, but only by soft deletion. A team member list can add or edit members, but deleting the only member of a team should be prohibited. Member changes must not alter historical feedback.
- **Inference:** Since members are stored only as names, the API needs some way to identify the acting member for member-only operations. The brief does not say whether that name is supplied in the request or how the server should validate it.
    - decision : No authentication is required; the caller-provided member name is trusted as an identity claim, but must be checked against the team's current member list for member-only operations.

> “A team must have at least one member”

- Does this apply only when creating a team, or also to later member-list updates? Membership updates are not specified.
    - decision : this applies at all times.

> “Any team member can create a retrospective for their team”

> “A retrospective can be closed by any team member”

- How does a request identify the member, given that authentication is explicitly out of scope?
    - decision : The member name must be included in the request, it must be validated that this member name is also in the list of team members of the retrospective.
- Must the supplied name match a current team member? If membership can change, does membership at the time of the request or at retrospective creation matter?
    - decision : The current members of the retrospective's team, at the time of the request, may modify that retrospective.

> “the name of the team member who submitted it”

- Must the submitter be a member of the retrospective's team? The wording suggests so, but does not explicitly require validation.
    - decision : Yes.
- How is the submitter identified or verified without authentication?
    - decision : The submitter's name must be included in the API request.

### Retrospectives

> “A retrospective has a title, a date, and a status: `OPEN` or `CLOSED`”

- What date format and meaning are required, such as a calendar date versus a timestamp? Are future dates allowed?
    - decision : The date is a UTC date-time represented in ISO 8601 format. Future dates are allowed.
- What validation applies to the title and date?
    - decision : A title must be a non-empty, non-whitespace UTF-8 string with a maximum length of 256 characters. Titles are globally unique, case-insensitively. The date is a non-null UTC date-time in ISO 8601 format.
- What status does a new retrospective start with? **Inference:** It likely starts `OPEN`, but the brief does not state a default or require clients to provide the status.
    - decision : A new retrospective can only start in status 'OPEN'.
- Are retrospective dates required to be unique?
    - decision : Retrospective dates do not need to be unique.

> “A team can have multiple retrospectives, but only one can be `OPEN` at a time”

- What should happen when a client tries to create an open retrospective while one is already open: reject the request, close the existing one, or something else? **Inference:** Rejecting creation is the least surprising behavior, but it is not specified.
    - decision : The creation is rejected, and the reasoning is returned as an error message.
- Must the rule also hold under simultaneous requests, not just sequential ones?
    - decision : The query for an open retrospective and creation of a new one must be performed in a transaction that takes a table-level lock, preventing simultaneous inserts.

> “Once a retrospective is closed, it cannot be reopened”

- What should the API return if a client tries to close an already closed retrospective? The rule forbids reopening, but does not define repeat-close behavior.
    - decision : Return HTTP 400 Bad Request with a clear error message in the response body.
- The brief does not explicitly say how retrospectives are retrieved, or whether they can be listed by team. **Inference:** Retrieval may be expected as part of “all operations described above,” but no retrospective retrieval operation is actually described.
    - decision : The inference is correct. Retrieval is part of the expected operations. Retrieval can happen for all retrospectives, all retrospectives belonging to a team, the open retrospective of a team and for a specific retrospective based on the name.

### Feedback items

> “A feedback item has content (text), a type (`WENT_WELL`, `NEEDS_IMPROVEMENT`, `ACTION_ITEM`), and the name of the team member who submitted it”

- What content validation applies: must text be non-empty, can it contain only whitespace, and are there length limits?
    - decision : Feedback content must be a non-empty UTF-8 string, at most 256 characters long, with no leading or trailing whitespace.
- Must the submitter be a current team member, and how is that checked?
    - decision : The submitter must be a current team member.
- Are feedback items editable or deletable while their retrospective is open? The brief defines a restriction for closed retrospectives but does not explicitly grant or describe the open-state operations.
    - decision : Yes, feedback items for open retrospectives can be modified and deleted.

> “Feedback items can be added to an `OPEN` retrospective”

- What response should the API give when an item is submitted to a closed or nonexistent retrospective?
    - decision : The requests should be rejected with a descriptive error message.
- Are action items created as feedback items or as a separate entity?
    - decision : Action items are a separate entity. Priority is required, and `completed` starts as `false`; a missing priority is invalid.

> “Feedback items in a `CLOSED` retrospective cannot be modified or deleted”

- Does this also forbid changing an action item's completion status after its retrospective closes? Marking it completed is a modification, but the brief does not resolve the interaction with the action-item completion rule.
    - decision : Yes. Action items cannot be modified after their retrospective closes.
- Does the API need modification and deletion endpoints? The brief defines when those operations are prohibited but does not explicitly require them.
    - decision : Yes. Feedback items in open retrospectives can be modified and deleted.

> “Feedback items can be retrieved per retrospective”

- Does this mean fetching one retrospective's full collection, fetching a single item, or both?
    - decision : Retrieve the collection of all non-action feedback items assigned to a retrospective; do not provide individual feedback-item retrieval.
- Should this collection include action items?
    - decision : No. Feedback retrieval excludes items of type `ACTION_ITEM`; action items are retrieved separately as their own entity.
- Are ordering, pagination, or other filters needed?
    - decision : Other list results may be unsorted; no additional feedback filters have been specified.

### Action items

> “An **action item** is a special kind of feedback item (type `ACTION_ITEM`) that additionally tracks a `priority` (`LOW`, `MEDIUM`, `HIGH`) and a boolean `completed` flag”

- Must priority be supplied when creating an action item? What is the initial value of `completed`?
    - decision : Priority is required and `completed` starts as `false`.
- What should happen if a request uses action-item fields with another feedback type, or creates an action item without a priority?
    - decision : Action items are separate entities, so action-item fields do not apply to other feedback types. Missing priority is invalid.
- Can priority or other action-item fields be changed? Can action items be deleted while their retrospective is open?
    - decision : Only content, priority, and completion status can be edited.
    - decision : Action items can be deleted while their retrospective is open.

> “Action items can be marked as completed”

> “Completed action items cannot be uncompleted”

- Can an action item be completed more than once, and what should happen on a repeated completion request?
    - decision : A repeated completion request returns HTTP 400 Bad Request with a clear error message.
- Who is allowed to mark it completed? The brief does not repeat the team-member restriction here.
    - decision : A current member of the team assigned to the retrospective may complete its action items.
- Is completion allowed after the retrospective closes?
    - decision : No. Closed-retrospective items cannot be modified.

> “Action items must be retrievable filtered by priority and by completed status”

- Is retrieval global, per team, per retrospective, or available at multiple scopes?
    - decision : A current member of a retrospective's team may retrieve that retrospective's action-item collection or a single action item by its database primary-key ID.
- Can priority and completed-status filters be combined, and what happens if a filter value is invalid?
    - decision : Priority and completion filters can be combined. An invalid filter value returns HTTP 400 with a clear message identifying the invalid value.
- Does “retrievable” require retrieving a single action item as well as filtered collections?
    - decision : Yes; single action items are retrieved by database primary-key ID.
- Are action-item lists ordered?
    - decision : Sort action items by priority from HIGH to LOW.

### API behavior and validation

> “All endpoints must return appropriate HTTP status codes”

- The brief does not define expected codes for successful creation.
- The brief does not define response bodies or error formats.
    - decision : Follow RESTful conventions. Successful responses return the affected entity in the body. Invalid operations return HTTP 400 with one or more clear error messages; missing resources return HTTP 404.

> “Input validation is required: invalid requests must return a `400` with a meaningful error message”

- What counts as invalid input versus a conflict with existing state, for example creating a second open retrospective or trying to modify a closed retrospective?
    - decision : Invalid operations, including invalid requests, return HTTP 400.
- What error-message format or detail is expected? Are multiple validation failures returned together or one at a time?
    - decision : The response body contains one or more clear error messages. Multiple validation errors may be returned together.

> “The API must support the following:”

- The brief does not specify HTTP methods, request/response JSON shapes, or whether clients can set IDs.
    - decision : Use RESTful conventions with entity endpoints under `/api/v1/{entity_name}`. Retrospectives are looked up by their globally unique title. Database-generated IDs are incremental.
- Are pagination or sorting needed for list endpoints?
    - decision : Sort retrospectives by date descending and action items by priority from HIGH to LOW. Other lists may be unsorted.
    - decision : Lists are not paginated.

### Implementation and completion expectations

> “Spring Data JPA with an in-memory H2 database (no persistence required between restarts)”

- The brief does not prescribe relationship mapping details such as deletion cascades or whether deleting a retrospective should be supported.
    - decision : All entities have an `IsDeleted` flag that is not exposed through the API. The API returns only entities whose `IsDeleted` value is `false`, and operations must not target entities whose flag is `true`.

> “A reasonable level of test coverage is expected: aim for the happy path plus the key business rules”

> “At least the following tested: retrospective closure rule, feedback item modification rule, action item completion rule”

- “Reasonable” and “happy path” do not define coverage thresholds or a complete test list. **Decision:** Derive test coverage from `retroflow-brief.md` and this document.
- “feedback item modification rule” points to a rule about modification; the decisions above specify that feedback items may be modified while their retrospective is open and not after it is closed.

> “All entities implemented with correct relationships”

- The brief gives conceptual ownership relationships but does not prescribe the JPA mapping or whether relationships are bidirectional. **Inference:** These are implementation choices unless the team has an unstated persistence convention.

## Review of recorded decisions

The prior open questions below are resolved by the decisions recorded above:

- Team names are unique case-insensitively and are trimmed of leading and trailing whitespace; member names must have no leading or trailing whitespace. Both are non-empty UTF-8 strings of at most 256 characters. A team must always retain at least one member. Member changes do not alter historical feedback.
- Teams may be soft-deleted, and members may be added, edited, or removed as long as one remains. Requests supply the member name; this is not authentication. Membership is checked against the retrospective team's current members at request time.
- Retrospective titles are globally unique without regard to case; dates need not be unique. Dates are UTC ISO 8601 date-times, future dates are allowed, and new retrospectives start OPEN. A table-level lock is required during the open-retrospective check and creation.
- Repeated retrospective closure returns HTTP 400 with a clear message. Retrospective retrieval includes all retrospectives, by team, a team's open retrospective, and a specific retrospective by its globally unique title.
- Feedback content is a non-empty UTF-8 string of at most 256 characters, with no leading or trailing whitespace. Feedback items may be modified or deleted while open, and cannot be changed after closure. Feedback retrieval returns the retrospective's non-action feedback-item collection only, not individual items; action items are retrieved separately.
- Action items are separate entities. Priority is required, `completed` starts as `false`, and only content, priority, and completion status are editable. They can be deleted while their retrospective is open. A repeated completion request returns HTTP 400. Current team members may complete action items, and closed-retrospective items cannot be modified.
- Current team members may retrieve action items by retrospective or retrieve a single item by database primary-key ID. Priority and completion filters may be combined; invalid filter values return HTTP 400 identifying the invalid value. Entity endpoints use RESTful conventions under `/api/v1/{entity_name}` and IDs are incrementally generated by the database. Successful responses return the affected entity; invalid operations return HTTP 400 and missing resources HTTP 404.
- Retrospectives are sorted by date descending, action items by priority from HIGH to LOW, and other lists may be unsorted. Lists are not paginated.
- All entities have an `IsDeleted` flag that is not exposed; API responses include only entities whose flag is `false`, and operations cannot target entities whose flag is `true`.
- Test coverage is derived from `retroflow-brief.md` and this document.

JPA relationship mapping and whether relationships are bidirectional remain implementation choices unless the team has a separate convention.

## Additional decisions

- Member-name uniqueness is case-insensitive within each team. A member may have the same name on another team.
- Team lookup and team-scoped operations identify the team by its name, case-insensitively. Retrospective lookup by title is also case-insensitive.
- Retrieving an empty collection returns HTTP 200 with an empty list. Retrospective date ties and action-item priority ties are ordered by database ID ascending.
- Soft-deleting a team also soft-deletes its retrospectives and their feedback and action items. Team deletion and team member-list changes do not require a current-member identity claim.
- Feedback-item requests that use `ACTION_ITEM` are rejected; action items must use the separate action-item operation. Action-item creation requires a submitter name that is validated against current team membership. If `completed=true` is supplied at creation, reject the request; new action items start incomplete.
- The feedback content validation rules also apply to action-item content. Feedback items in open retrospectives may be modified or deleted by any current team member; only content and type may be modified, and the submitter and parent retrospective cannot be changed. Feedback deletion is soft.
- Action-item content and priority may be modified, and action items may be deleted, by any current team member while the retrospective is open. Action-item deletion is soft. Modification requests containing fields other than content, priority, or completion status are rejected. Only current team members may complete action items.
- All successful operations return HTTP 200. Error bodies contain an `errors` array with one or more clear messages.
- Team member-list management and team deletion are permitted without member validation. This does not change the current-membership checks for other member-only operations.
