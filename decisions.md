# RetroFlow: Domain and Requirements Questions

This document explains the domain and records requirements that may need clarification. It is a review draft: edit or remove items that the team agrees are already clear.

## Domain overview

RetroFlow records a team's retrospectives and the feedback gathered during them. A **team** has a name and a list of member names. Each **retrospective** belongs to one team and has a title, date, and `OPEN` or `CLOSED` status. A team may have several retrospectives, but at most one can be open at a time; once closed, a retrospective stays closed.

A **feedback item** belongs to a retrospective and records text, a type (`WENT_WELL`, `NEEDS_IMPROVEMENT`, or `ACTION_ITEM`), and the submitting member's name. **Action items** are feedback items with extra fields: priority and completion status. Items can be added to open retrospectives; items in closed retrospectives cannot be changed or deleted. Completed action items cannot be marked incomplete.

**Inference:** A natural domain model is Team → Retrospectives → Feedback items, with ActionItem either a subtype of FeedbackItem or an equivalent representation that adds priority and completion fields. The brief does not prescribe the implementation.

## Ambiguities and missing details

Each section quotes the relevant sentence from the brief. **Inference** labels interpretations that seem plausible but are not specified requirements.

### Teams and membership

> “A **team** has a name and a list of members (stored as names, no authentication required)”

- Are team names required to be unique? Are member names unique within a team, or can two people share a name?
- What counts as a valid name: can it be blank or whitespace-only, and are there length limits?
- Can teams or their member lists be updated or deleted? The brief only specifies creating and retrieving teams.
- **Inference:** Since members are stored only as names, the API needs some way to identify the acting member for member-only operations. The brief does not say whether that name is supplied in the request or how the server should validate it.

> “A team must have at least one member”

- Does this apply only when creating a team, or also to later member-list updates? Membership updates are not specified.

> “Any team member can create a retrospective for their team”

> “A retrospective can be closed by any team member”

- How does a request identify the member, given that authentication is explicitly out of scope?
- Must the supplied name match a current team member? If membership can change, does membership at the time of the request or at retrospective creation matter?

> “the name of the team member who submitted it”

- Must the submitter be a member of the retrospective's team? The wording suggests so, but does not explicitly require validation.
- How is the submitter identified or verified without authentication?

### Retrospectives

> “A retrospective has a title, a date, and a status: `OPEN` or `CLOSED`”

- What date format and meaning are required, such as a calendar date versus a timestamp? Are future dates allowed?
- What validation applies to the title and date?
- What status does a new retrospective start with? **Inference:** It likely starts `OPEN`, but the brief does not state a default or require clients to provide the status.
- Are retrospective titles or dates required to be unique within a team?

> “A team can have multiple retrospectives, but only one can be `OPEN` at a time”

- What should happen when a client tries to create an open retrospective while one is already open: reject the request, close the existing one, or something else? **Inference:** Rejecting creation is the least surprising behavior, but it is not specified.
- Must the rule also hold under simultaneous requests, not just sequential ones?

> “Once a retrospective is closed, it cannot be reopened”

- What should the API return if a client tries to close an already closed retrospective? The rule forbids reopening, but does not define repeat-close behavior.
- The brief does not explicitly say how retrospectives are retrieved, or whether they can be listed by team. **Inference:** Retrieval may be expected as part of “all operations described above,” but no retrospective retrieval operation is actually described.

### Feedback items

> “A feedback item has content (text), a type (`WENT_WELL`, `NEEDS_IMPROVEMENT`, `ACTION_ITEM`), and the name of the team member who submitted it”

- What content validation applies: must text be non-empty, can it contain only whitespace, and are there length limits?
- Must the submitter be a current team member, and how is that checked?
- Are feedback items editable or deletable while their retrospective is open? The brief defines a restriction for closed retrospectives but does not explicitly grant or describe the open-state operations.

> “Feedback items can be added to an `OPEN` retrospective”

- What response should the API give when an item is submitted to a closed or nonexistent retrospective?
- Does “added” include both ordinary feedback and action items, or do action items use a separate creation operation? **Inference:** Action items are probably created as feedback items with type `ACTION_ITEM`, but no endpoint or request shape is specified.

> “Feedback items in a `CLOSED` retrospective cannot be modified or deleted”

- Does this also forbid changing an action item's completion status after its retrospective closes? Marking it completed is a modification, but the brief does not resolve the interaction with the action-item completion rule.
- Does the API need modification and deletion endpoints? The brief defines when those operations are prohibited but does not explicitly require them.

> “Feedback items can be retrieved per retrospective”

- Does this mean fetching one retrospective's full collection, fetching a single item, or both?
- Should this collection include action items? **Inference:** Yes, because action items are described as a kind of feedback item.
- Are ordering, pagination, or other filters needed?

### Action items

> “An **action item** is a special kind of feedback item (type `ACTION_ITEM`) that additionally tracks a `priority` (`LOW`, `MEDIUM`, `HIGH`) and a boolean `completed` flag”

- Must priority be supplied when creating an action item? What is the initial value of `completed`? **Inference:** `completed` likely starts `false`, but no default is specified.
- What should happen if a request uses action-item fields with another feedback type, or creates an `ACTION_ITEM` without a priority?
- Can priority or other action-item fields be changed? Can action items be deleted while their retrospective is open?

> “Action items can be marked as completed”

> “Completed action items cannot be uncompleted”

- Can an action item be completed more than once, and what should happen on a repeated completion request?
- Who is allowed to mark it completed? The brief does not repeat the team-member restriction here.
- Is completion allowed after the retrospective closes?

> “Action items must be retrievable filtered by priority and by completed status”

- Is retrieval global, per team, per retrospective, or available at multiple scopes?
- Can priority and completed-status filters be combined, and what happens if a filter value is invalid?
- Does “retrievable” require retrieving a single action item as well as filtered collections?

### API behavior and validation

> “All endpoints must return appropriate HTTP status codes”

- The brief does not define expected codes for successful creation, missing resources, duplicate/open-retrospective conflicts, invalid state transitions, or other business-rule violations. It only specifies a status for invalid input in the next sentence.
- It does not define response bodies, error formats, or whether errors should be consistent across endpoints.

> “Input validation is required: invalid requests must return a `400` with a meaningful error message”

- What counts as invalid input versus a conflict with existing state, for example creating a second open retrospective or trying to modify a closed retrospective?
- What error-message format or detail is expected? Are multiple validation failures returned together or one at a time?

> “The API must support the following:”

- The brief does not specify endpoint paths, HTTP methods, request/response JSON shapes, resource identifiers, or whether clients can set IDs.
- Are pagination or sorting needed for list endpoints?

### Implementation and completion expectations

> “Spring Data JPA with an in-memory H2 database (no persistence required between restarts)”

- The brief does not prescribe relationship mapping details such as deletion cascades, or whether deleting a team or retrospective should be supported. **Inference:** Deletion behavior matters only if those delete operations are added, and the brief does not require them.

> “A reasonable level of test coverage is expected: aim for the happy path plus the key business rules”

> “At least the following tested: retrospective closure rule, feedback item modification rule, action item completion rule”

- “Reasonable” and “happy path” do not define coverage thresholds or a complete test list. The minimum tests are named, but the expected cases within each rule, such as repeat requests or behavior on closed retrospectives, are not.
- “feedback item modification rule” points to a rule about modification, but the brief does not describe any modification operation or its allowed behavior while a retrospective is open.

> “All entities implemented with correct relationships”

- The brief gives conceptual ownership relationships, but does not define the required JPA mapping, whether relationships are bidirectional, or whether action items must be a Java inheritance subtype versus a separate linked entity. **Inference:** These are implementation choices unless the team has an unstated persistence convention.