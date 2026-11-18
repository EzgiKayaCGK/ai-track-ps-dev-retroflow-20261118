# RetroFlow business rules

Source: `retroflow-brief.md` and `decisions.md`. All successful operations return HTTP 200.

| rule | operation | situation | expected result | exception | HTTP status |
|---|---|---|---|---|---|
| Team names are non-empty, non-whitespace UTF-8 strings of at most 256 characters | Create team | Name is blank or exceeds the limit | Reject the request | Trim leading/trailing whitespace before storing a valid name | 400 |
| Team names are trimmed of leading and trailing whitespace | Create team | Name has surrounding whitespace and is otherwise valid | Store the trimmed team name | None stated | 200 |
| Team names are unique case-insensitively | Create team | A team with the same name, ignoring case, already exists | Reject the duplicate | None stated | 400 |
| A team must have at least one member | Create team | No members are provided | Reject the request | None stated | 400 |
| Member names are non-empty, non-whitespace UTF-8 strings of at most 256 characters, with no surrounding whitespace | Create team, add, or edit member | Name is blank, too long, or has leading/trailing whitespace | Reject the request | None stated | 400 |
| Member names must be unique case-insensitively within a team | Create team, add, or edit member | A duplicate member name ignoring case is supplied for that team | Reject the duplicate | The same name may be used on another team | 400 |
| Team lookup and team-scoped operations identify teams by name, case-insensitively | Retrieve, delete, or change the member list for a team | The name identifies an active team | Apply the requested operation to that team | Deleted or missing team returns 404; member-list changes and deletion do not require member validation | 200 |
| A team may be soft-deleted | Delete team | Delete is requested for an active team | Mark the team, its retrospectives, and their feedback and action items deleted | Deletion does not require member validation; deletion flag is not exposed | 200 |
| The `IsDeleted` flag is not exposed through the API | Create, retrieve, or modify any entity | A response would otherwise include the flag | Omit `IsDeleted` from the API representation | None stated | 200 |
| Deleted entities are not returned and cannot be targeted | Retrieve or operate on a soft-deleted entity | The requested entity has `IsDeleted = true` | Treat it as unavailable | None stated | 404 |
| A member list can be changed without changing historical feedback | Add, edit, or remove member | A member is added, renamed, or removed | Preserve historical feedback as recorded | These operations do not require member validation | 200 |
| A team must always have at least one member | Remove member | Removal would leave the team with no members | Reject the removal | None stated | 400 |
| The request supplies a member name, which is trusted as an identity claim but checked against the team's current membership for member-only operations | Any member-only operation | Supplied name is not a current member of the relevant team | Reject the operation | Authentication is not required | 400 |
| Retrospective titles are non-empty, non-whitespace UTF-8 strings of at most 256 characters | Create retrospective | Title is blank or exceeds the limit | Reject the request | None stated | 400 |
| Retrospective titles are globally unique case-insensitively | Create retrospective | Another retrospective has the same title, ignoring case | Reject the request | None stated | 400 |
| A retrospective date is a non-null UTC date-time in ISO 8601 format | Create retrospective | Date is missing or invalid/not UTC ISO 8601 | Reject the request | Future dates are allowed | 400 |
| Retrospective dates need not be unique | Create retrospective | Another retrospective has the same date | Allow creation if all other rules pass | None stated | 200 |
| A retrospective belongs to a team | Create retrospective | The referenced team is missing or deleted | Do not create the retrospective | None stated | 404 |
| Any team member can create a retrospective for their team | Create retrospective | Caller is a current member of the team | Allow creation if all other rules pass | A non-member is rejected under the member-identity rule | 200 |
| A new retrospective starts OPEN | Create retrospective | Creation succeeds | Set status to `OPEN`; clients cannot create it directly as `CLOSED` | None stated | 200; 400 if an invalid status is supplied |
| A team can have at most one OPEN retrospective | Create retrospective | The team already has an OPEN retrospective | Reject creation and return the reason in an error message | Check and creation must be transactional with a table-level lock | 400 |
| Retrospective status is OPEN or CLOSED | Create or close retrospective | A status other than the allowed lifecycle states is requested | Reject the invalid operation | Reopening a closed retrospective is separately prohibited | 400 |
| A retrospective can be closed by any current team member | Close retrospective | Caller is a current member and the retrospective is OPEN | Change status to `CLOSED` | A non-member is rejected under the member-identity rule | 200 |
| A closed retrospective cannot be reopened | Reopen retrospective | Retrospective is CLOSED | Reject; retain CLOSED status | None stated | 400 |
| A retrospective cannot be closed repeatedly | Close retrospective | Retrospective is already CLOSED | Reject with a clear error message | None stated | 400 |
| Retrospectives can be retrieved in the supported scopes | Retrieve retrospectives | Request is for all, all for a team, a team's OPEN retrospective, or a specific title | Return the matching non-deleted retrospective(s) | Missing team/retrospective returns 404; an empty collection returns an empty list | 200 |
| Retrospective titles are the lookup key for a specific retrospective | Retrieve retrospective by title | Requested title does not identify a non-deleted retrospective | Treat the retrospective as missing | Title lookup is case-insensitive; titles are globally unique case-insensitively | 404 |
| Retrospective lists are sorted by date descending | Retrieve retrospective list | More than one result is returned | Return newest dates first | Equal dates are ordered by database ID ascending | 200 |
| Lists are not paginated | Retrieve any list | Result set is larger than one page would be | Return the full result set | None stated | 200 |
| Empty collection retrieval returns an empty list | Retrieve any collection | No records match the request | Return an empty list | Missing resources for singular lookups still return 404 | 200 |
| Feedback content is a non-empty UTF-8 string of at most 256 characters with no surrounding whitespace | Create or modify feedback item or action item | Content is blank, too long, or has leading/trailing whitespace | Reject the request | None stated | 400 |
| Feedback item type is WENT_WELL or NEEDS_IMPROVEMENT | Create or modify feedback item | An unsupported type or `ACTION_ITEM` is supplied | Reject the request; create action items only through the separate action-item operation | None stated | 400 |
| A feedback submitter must be a current member of the retrospective's team | Create feedback item | Submitter is not a current member | Reject the request | Submitter name must be included in the request | 400 |
| Feedback can be added only to an OPEN retrospective | Add feedback item | Parent retrospective is OPEN | Create the item if all other rules pass | None stated | 200 |
| Feedback can be added only to an OPEN retrospective | Add feedback item | Parent retrospective is CLOSED | Reject with a descriptive error | None stated | 400 |
| Feedback must belong to a retrospective | Add or retrieve feedback | Parent retrospective is missing or deleted | Do not add/return feedback for it | None stated | 404 |
| Feedback in a CLOSED retrospective cannot be modified | Modify feedback item | Parent retrospective is CLOSED | Reject; leave item unchanged | None stated | 400 |
| Feedback in a CLOSED retrospective cannot be deleted | Delete feedback item | Parent retrospective is CLOSED | Reject; leave item unchanged | None stated | 400 |
| Feedback in an OPEN retrospective may be modified | Modify feedback item | Parent retrospective is OPEN | Any current team member may change content or type | Submitter and parent retrospective cannot be changed | 200 |
| Feedback in an OPEN retrospective may be deleted | Delete feedback item | Parent retrospective is OPEN | Any current team member may soft-delete it | None stated | 200 |
| Feedback retrieval returns the retrospective's collection, not an individual feedback item | Retrieve feedback | Request is for feedback belonging to a retrospective | Return the collection | Individual feedback-item retrieval is not provided | 200 |
| Feedback retrieval excludes action items | Retrieve feedback | Collection contains action items | Do not include action items in the feedback result | Action items are retrieved separately | 200 |
| Other lists may be unsorted | Retrieve feedback list or other non-retrospective/non-action-item list | Multiple results are returned | Any ordering is acceptable | None stated | 200 |
| Action items are separate entities, with required priority and `completed = false` initially | Create action item | Valid action-item creation with a submitter who is a current team member | Create a separate ActionItem with the supplied priority, submitter name, and `completed` false | A missing or non-member submitter is invalid | 200 |
| `completed` starts as false | Create action item | Request explicitly supplies `completed = true` | Reject the request | Omitted `completed` defaults to false | 400 |
| Feedback items can be added only to an OPEN retrospective | Add action item | Parent retrospective is OPEN | Create the action item if all other rules pass | None stated | 200 |
| Feedback items can be added only to an OPEN retrospective | Add action item | Parent retrospective is CLOSED | Reject with a descriptive error | None stated | 400 |
| Action-item priority is LOW, MEDIUM, or HIGH and is required | Create or modify action item | Priority is missing or invalid | Reject the request | None stated | 400 |
| Action-item content is editable | Modify action item | Content change is requested | Allow any current team member to change content if the retrospective is OPEN and other rules pass | Feedback content validation also applies | 200 |
| Only content, priority, and completion status are editable on an action item | Modify action item | A different field is changed | Reject the request | None stated | 400 |
| Only a current team member may complete an action item | Modify action item completion status | Request changes `completed` from false to true | Require the caller to be a current member of the retrospective's team | None stated | 400 for a non-member |
| Action items cannot be modified after their retrospective closes | Modify action item | Parent retrospective is CLOSED | Reject; leave item unchanged | Includes content, priority, and completion status | 400 |
| Action items can be deleted while their retrospective is OPEN | Delete action item | Parent retrospective is OPEN | Any current team member may soft-delete the action item | None stated | 200 |
| Action items cannot be modified after their retrospective closes | Delete action item | Parent retrospective is CLOSED | Reject; leave item unchanged | None stated | 400 |
| Completed action items cannot be uncompleted | Mark action item incomplete or modify its completion status | Item is already completed | Reject; retain completed status | None stated | 400 |
| Action items can be marked completed | Complete action item | Item is not completed and parent retrospective is OPEN | Set `completed` to true | Caller must be a current member of the retrospective's team | 200 |
| Only a current team member may complete an action item | Complete action item | Caller is not a current member | Reject the request | None stated | 400 |
| An action item in a CLOSED retrospective cannot be modified | Complete action item | Parent retrospective is CLOSED | Reject; do not change completion status | None stated | 400 |
| A completed action item cannot be completed again | Complete action item | Item is already completed | Reject with a clear error message | None stated | 400 |
| Action items are retrievable per retrospective by a current member | Retrieve action-item collection | Caller is a current member of the parent team's current membership | Return that retrospective's action-item collection | Non-members are rejected as invalid operations | 200 |
| A current member may retrieve a single action item by database primary-key ID | Retrieve action item | ID identifies a non-deleted item and caller is a current member of its retrospective's team | Return the item | Missing/deleted item returns 404; a non-member is rejected as an invalid operation | 200 |
| Action-item priority and completed filters may be combined | Retrieve action-item collection | One or both valid filters are provided | Return items matching all supplied filters | None stated | 200 |
| Invalid action-item filter values return 400 and identify the invalid value | Retrieve action-item collection | Priority or completed filter is invalid | Reject with a clear message naming the invalid value | None stated | 400 |
| Action-item lists are sorted HIGH to LOW priority | Retrieve action-item collection | Multiple priorities are returned | Return HIGH before MEDIUM before LOW | Items with the same priority are ordered by database ID ascending | 200 |
| Successful responses return the affected entity; invalid operations return clear error message(s) | Any operation | Operation succeeds or is invalid | Return the affected entity on success; error response contains an `errors` array with one or more clear messages | None stated | 200; invalid operation: 400 |
| Missing resources return 404 | Retrieve or operate on a missing resource | Requested resource does not exist or is unavailable because it is deleted | Return a not-found response | None stated | 404 |
