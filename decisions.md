# RetroFlow: Converged Decisions

This document combines the decisions in `decisions.md` and `decisions2.md` with
follow-up clarifications. Conflicts are resolved using the project brief and the
API contract documented in `README.md`.

## Input normalization and validation

- Leading and trailing whitespace is trimmed from all names, titles, and content
  on creation, update, and lookup. Input is not rejected for surrounding
  whitespace; the trimmed value must still satisfy every other requirement.
- Name, title, and content comparisons are case-insensitive, including uniqueness
  checks and lookups.
- Length is measured as string length after trimming.
- Team names and member names must be non-empty and at most 100 characters,
  matching the `X-User-Name` header limit. Retrospective titles and feedback
  content must be non-empty and at most 256 characters.

## Teams and membership

- Team names are unique across the system. A member can belong to one or more
  teams. Member names are unique within a team, so the same name cannot appear
  twice in one team's member list.
- Team names and retrospective titles cannot be reused. Uniqueness checks include
  soft-deleted teams and retrospectives. Member names can be reused: a removed
  member can be added back to the same team or join other teams.
- A team is created with at least one member. The caller from `X-User-Name` is
  always included as a member of the new team, even if omitted from the request.
- Every team must have at least one member at all times. Members can be added,
  edited, or removed, but the last member cannot be removed. A member may remove
  themselves, or omit the caller from the replacement list, as long as at least
  one member remains. Membership changes do not alter historical feedback.
- Teams can be retrieved by ID or team name. A missing team returns
  `404 Not Found`. Looking up by member name returns a list of teams that include
  that member; the list contains only teams where the caller is also a current
  member and may be empty.
- Teams can be soft-deleted. Soft deletion cascades to the team's
  retrospectives and their feedback items, including action items.

## Caller identity and authorization

- No authentication layer is required. The caller supplies their member name in
  the `X-User-Name` header. This is a trusted identity claim, not verified
  authentication.
- The header is required and must contain a nonblank name of at most 100
  characters. A missing or invalid header returns `400 Bad Request`.
- Creating a team does not require membership. Every other operation, including
  reads, requires the caller to be a current member of the owning team at
  request time. A non-member receives `403 Forbidden`.
- The only exception is `GET /api/retrospectives`, which lists retrospectives
  across all teams and is open to any caller with a valid header.
- A feedback item's submitter is the caller from `X-User-Name`. The submitter
  cannot be changed later.

## Retrospectives

- A retrospective belongs to one team and has a title, date, and status: `OPEN`
  or `CLOSED`. New retrospectives start `OPEN`.
- Titles are unique across all teams. A duplicate title returns `409 Conflict`.
- A client-sent date is used as given, with no validation. If none is sent, the
  server assigns the current UTC date-time. Dates are returned in ISO 8601
  format and do not need to be unique.
- A team can have at most one open retrospective. Creating another while one is
  open returns `400 Bad Request`. See [Concurrency](#concurrency).
- Closing an already closed retrospective returns `400 Bad Request`. A closed
  retrospective cannot be reopened, and no reopen endpoint exists.
- Retrospectives can be retrieved as a full list, by team, as a team's open
  retrospective, or individually by ID or title. If a team has no open
  retrospective, the open-retrospective lookup returns `200 OK` with an empty
  body.
- When creating a retrospective, checks run in this order: membership (`403`),
  then duplicate title (`409`), then all other checks in no defined order.

## Feedback items and action items

- Feedback items and action items are one entity. A feedback item belongs to a
  retrospective and has content, a type (`WENT_WELL`, `NEEDS_IMPROVEMENT`, or
  `ACTION_ITEM`), and the submitting member's name.
- Items with type `ACTION_ITEM` also have a `priority` (`LOW`, `MEDIUM`, or
  `HIGH`) and a `completed` flag. Priority is required for action items.
  `completed` defaults to `false`, but an action item created with
  `completed=true` is accepted. Other types must not include `priority` or
  `completed`.
- Items can be added, modified, or deleted only while their retrospective is
  open. Changes to items in a closed retrospective return `400 Bad Request`.
  Requests for a nonexistent retrospective return `404 Not Found`. Completing an
  action item is exempt: it is allowed in a closed retrospective.
- Any current team member can modify or delete any item, not only its submitter.
- Editable fields are content and type. For action items, priority is also
  editable. The submitter is never editable: a submitter sent in a request is
  ignored and the original is kept.
- Changing an item to `ACTION_ITEM` requires a priority and sets `completed` to
  `false`. Changing an action item to another type removes its priority and
  completion status.
- A completed action item cannot be edited at all (content, type, priority, or
  completion status); the request returns `400 Bad Request`. It can still be
  deleted, subject to the open-retrospective rule.
- Any current team member can complete an action item. A completed item cannot
  be uncompleted. Repeating a completion request returns `400 Bad Request`.
- Endpoints that expect an action item (`GET /api/action-items/{id}` and
  `PUT /api/action-items/{id}/complete`) return `400 Bad Request` with a clear
  message when the ID belongs to a feedback item that is not an `ACTION_ITEM`.

### Retrieval

- Feedback retrieval returns all items for a retrospective except
  `ACTION_ITEM` items. There is no endpoint for retrieving one feedback item.
- Action-item retrieval returns a retrospective's `ACTION_ITEM` items or one
  action item by ID. `priority` and `completed` filters can be combined. Invalid
  filter values return `400 Bad Request` and identify the invalid value.

## API behavior

- Follow the routes, header, and error format documented in `README.md`.
  Additional endpoints follow the same style: resources under `/api/...` with
  numeric IDs in path variables.

  | Method and path | Purpose |
  | --- | --- |
  | `GET /api/teams?name=` | Look up a team by name |
  | `GET /api/teams?member=` | List teams that include a member |
  | `PUT /api/teams/{id}/members` | Replace a team's member list |
  | `DELETE /api/teams/{id}` | Soft-delete a team and cascade |
  | `GET /api/retrospectives` | List all retrospectives (no membership required) |
  | `GET /api/retrospectives?title=` | Look up a retrospective by title |
  | `GET /api/retrospectives/{id}` | Get a retrospective by ID |
  | `GET /api/teams/{teamId}/retrospectives/open` | Get a team's open retrospective |
  | `POST /api/retrospectives/{id}/feedback` | Add a feedback item or action item |
  | `GET /api/retrospectives/{id}/feedback` | List non-action feedback items |
  | `PUT /api/feedback/{id}` | Update a feedback item or action item |
  | `DELETE /api/feedback/{id}` | Delete a feedback item or action item |
  | `GET /api/retrospectives/{id}/action-items?priority=&completed=` | List and filter action items |
  | `GET /api/action-items/{id}` | Get one action item |
  | `PUT /api/action-items/{id}/complete` | Mark an action item completed |

- Creation returns `201 Created` with the created entity. Soft deletes, including
  feedback and action item deletes, return `204 No Content` without a body. Other
  successful operations return `200 OK` with the affected entity.
- Errors use the JSON shape `{"message":"..."}`. Multiple validation errors may be
  combined into one message.
- Status codes:
  - `400 Bad Request`: invalid input or an invalid state transition.
  - `403 Forbidden`: the caller is not a current member of the team.
  - `404 Not Found`: the resource is missing or soft-deleted.
  - `409 Conflict`: a team name or retrospective title is duplicated, or a
    member name is duplicated within the same team.
- Exceptions map to the existing handlers in `ApiExceptionHandler`:
  - `RetroflowException` (`400`): business-rule and state-transition violations,
    invalid `X-User-Name` header length or blankness, and invalid retrospective
    titles.
  - `MissingRequestHeaderException` (`400`): missing `X-User-Name` header.
  - `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, and
    `MethodArgumentTypeMismatchException` (`400`): other invalid bodies,
    malformed JSON, and invalid enum or filter values.
  - `ForbiddenException` (`403`), `ResourceNotFoundException` (`404`), and
    `ConflictException` (`409`), including lock timeouts.
- Lists are not paginated. Retrospectives are sorted by date descending. Action
  items are sorted by priority from `HIGH` to `LOW`. Ties and other lists have no
  defined order.

## Persistence

- IDs are generated incrementally by the database.
- All entities have an internal soft-delete flag that is not exposed by the API.
  Responses include only entities that are not deleted, and operations cannot
  target deleted entities.
- JPA relationship mapping, including whether relationships are bidirectional,
  is an implementation choice.

## Concurrency

- Run open-retrospective creation in a `@Transactional` service method. First,
  acquire a pessimistic write lock on the team row using
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Then check for an open retrospective
  and insert the new one. This serializes creation per team without locking
  other teams, which is narrower than the table-level lock from `decisions2.md`.
  A request that waits for the lock sees the newly created open retrospective
  and receives `400 Bad Request`.
- Use the same team-row lock for member-list changes, so the last-member and
  member-uniqueness checks cannot race with each other.
- Back uniqueness rules with database unique constraints on normalized
  values: trimmed lowercase team names, retrospective titles, and
  `(team, member name)` pairs for active members. Map any constraint violation that escapes a service-level check to
  `409 Conflict`.
- Use a short lock timeout. If the lock cannot be acquired, return
  `409 Conflict` with a message asking the client to retry.

## Testing

- Derive test coverage from the project brief and these decisions. Tests must
  cover at least the retrospective closure rule, feedback-item modification rule,
  action-item completion rule, membership authorization, and soft-delete
  cascade.
