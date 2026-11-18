# RetroFlow coding-standards audit

This audit compares the repository with **StackCraft Engineering — Coding Standards, v3.1**. Findings are sorted by the severity specified in that document. No files under `src/` were changed.

## Critical

### 1. Resource creation endpoints return `200 OK`, not `201 Created`

**Location:** `src/main/java/com/stackcraft/retroflow/controller/RetroflowController.java:41-45, 62-66, 80-91, 125-131, 160-167`; the current expectation is also encoded in `src/test/java/com/stackcraft/retroflow/RetroflowBusinessRulesMatrixTest.java:538-550`.

**Standard — §4.1:** “Resource created | 201 Created”; “Using 200 for a creation [...] is a Critical violation.”

**Why it matters:** These `POST` handlers create teams, members, retrospectives, feedback items, and action items, but return response bodies without setting a status, so Spring returns `200 OK`. The integration test explicitly expects that noncompliant status.

**Correct implementation:** Return `201 Created` for each successful resource-creation operation, using `ResponseEntity.created(...)` where a resource URI is available or `@ResponseStatus(HttpStatus.CREATED)` otherwise. Align the existing status assertions with the required contract when tests are next maintained.

### 2. Delete endpoints return a response body and `200 OK`

**Location:** `src/main/java/com/stackcraft/retroflow/controller/RetroflowController.java:57-60, 75-78, 143-147, 193-197`.

**Standard — §4.1:** “Successful delete | 204 No Content.”

**Why it matters:** Each delete handler returns the deleted entity response. With no explicit status, clients receive `200 OK` and a body rather than the required `204 No Content`.

**Correct implementation:** Return `ResponseEntity.noContent().build()` (or an equivalent `204` response) and do not serialize a response body for successful deletes.

### 3. Business-rule violations are mapped to `400 Bad Request`

**Location:** `src/main/java/com/stackcraft/retroflow/controller/RetroflowExceptionHandler.java:25-27`; business-rule exceptions include `src/main/java/com/stackcraft/retroflow/service/RetrospectiveService.java:63-66, 111-116, 183-184, 207-210` and `src/main/java/com/stackcraft/retroflow/service/TeamService.java:36-49, 105-110`.

**Standard — §4.1:** “Business rule violation | 409 Conflict”; “Using [...] 400 for a business rule violation, is a Critical violation.”

**Why it matters:** The handler maps every `RetroflowException` to `400`, including conflicts such as duplicate/open retrospectives, modifying a closed retrospective, and removing a team's last member. This makes domain conflicts indistinguishable from invalid input.

**Correct implementation:** Separate business-rule conflicts from validation failures (for example, with distinct exception types) and map business-rule exceptions to `409 Conflict`, retaining `400 Bad Request` for malformed or invalid input.

### 4. Service-layer exception messages do not use the required format

**Location:** `src/main/java/com/stackcraft/retroflow/service/RetrospectiveService.java:48, 63, 66, 105, 115, 183, 210, 220, 231-244, 277-310, 314-374`; `src/main/java/com/stackcraft/retroflow/service/TeamService.java:39, 42, 49, 109, 123, 129, 136, 143, 151, 155`.

**Standard — §2.1:** “All exceptions thrown by the service layer must follow this exact format: `[EntityType] [identifier]: [human-readable reason]`.” “This format is required because our observability platform parses exception messages for alerting and dashboards.”

**Why it matters:** Service exceptions currently use generic messages such as `"The team already has an open retrospective."`, `"The retrospective is already closed."`, and `"Team not found."` without the entity type and identifier. Monitoring cannot reliably extract the entity and resource involved.

**Correct implementation:** Format every service-layer exception with the affected type and identifier, such as `"Retrospective 42: cannot add feedback to a closed retrospective"`; include the relevant entity/name identifier when constructing not-found and validation exceptions too.

### 5. Error responses do not follow the required JSON schema

**Location:** `src/main/java/com/stackcraft/retroflow/controller/ApiModels.java:44-45`; `src/main/java/com/stackcraft/retroflow/controller/RetroflowExceptionHandler.java:19-33`.

**Standard — §2.2:** “Every project must have a `@RestControllerAdvice` class that handles all custom exceptions and maps them to structured JSON error responses.” Required format: `{"error":"EntityType identifier: reason","status":400,"timestamp":"2025-03-15T10:30:00Z"}`.

**Why it matters:** A global advice exists, but its `ErrorResponse` exposes only `List<String> errors`; responses therefore omit the required scalar `error`, numeric `status`, and timestamp fields. Consumers cannot rely on the specified contract.

**Correct implementation:** Replace the errors-list response model with the required `error`, `status`, and ISO-8601 `timestamp` fields, and populate them consistently in each exception handler.

## Major

### 6. Request validation is deferred to the service layer

**Location:** `src/main/java/com/stackcraft/retroflow/controller/RetroflowController.java:42-45, 63-66, 81-90, 126-131, 161-166`; service-level validation examples: `src/main/java/com/stackcraft/retroflow/service/TeamService.java:36-59, 132-156` and `src/main/java/com/stackcraft/retroflow/service/RetrospectiveService.java:314-365`.

**Standard — §5.2:** “All data entering the system via API must be validated before it reaches the service layer. The controller is the boundary. Do not assume the service will catch bad data.”

**Why it matters:** Typed request handlers only call `requireRequest` before passing fields such as team name, member names, feedback content/type, and submitted-by name into services. The services then perform the substantive field checks. The map-based handlers similarly check basic shape before calling service methods that validate business inputs.

**Correct implementation:** Put boundary constraints on request DTOs and invoke them with `@Valid`; validate map-based payloads at the controller boundary as well. Keep service checks for domain invariants, but do not rely on them for API-input validation.

### 7. Public service methods lack JavaDoc

**Location:** `src/main/java/com/stackcraft/retroflow/service/RetrospectiveService.java:45-271` and `src/main/java/com/stackcraft/retroflow/service/TeamService.java:36-115` (public service methods).

**Standard — §6.1:** “Every public method in a service class must have a JavaDoc comment” describing the method, documenting each `@param` and applicable `@throws`, and documenting `@return` when it returns a value. The standard says severity “rises to Major for methods implementing business rules.”

**Why it matters:** These public methods implement the application’s business operations and rules, but none has the required JavaDoc, leaving callers without the documented behavior, parameter meanings, or exception contract.

**Correct implementation:** Add JavaDoc to every public method in both services, including descriptions and complete applicable `@param`, `@throws`, and `@return` tags.

## Minor

### 8. Production and test classes exceed the 200-line limit

**Location:** `src/main/java/com/stackcraft/retroflow/controller/RetroflowController.java:31-277` (277 lines); `src/main/java/com/stackcraft/retroflow/service/RetrospectiveService.java:27-377` (377 lines); `src/test/java/com/stackcraft/retroflow/RetroflowBusinessRulesMatrixTest.java:37-664` (664 lines); `src/test/java/com/stackcraft/retroflow/ServiceMutationTest.java:33-253` (253 lines).

**Standard — §1.2:** “No class may exceed 200 lines. If a class approaches this limit, it is doing too much.” Violation severity: **Minor**.

**Why it matters:** These classes exceed the prescribed limit, making responsibilities and tests harder to navigate and maintain.

**Correct implementation:** Split the controller and retrospective service along cohesive responsibilities. Divide the large matrix and mutation test classes into focused test classes without reducing their coverage.

### 9. Test names do not follow the prescribed convention

**Location:** Examples include `src/test/java/com/stackcraft/retroflow/RetroflowBusinessRulesTest.java:39, 54, 66`, `src/test/java/com/stackcraft/retroflow/RetroflowBusinessRulesMatrixTest.java:52, 538`, and `src/test/java/com/stackcraft/retroflow/ServiceMutationTest.java:63, 76`.

**Standard — §3.2:** “Test method names must follow: `methodName_condition_expectedBehaviour`.” Violation severity: **Minor**.

**Why it matters:** Names such as `closingARetrospective_preventsAddingFeedback`, `teamNames_areRequiredAndLimitedTo256Characters`, and `apiUsesHttp200ForSuccessAndReturnsEntityOrAnErrorsArray` do not identify the tested method followed by a condition and expected behavior in the required format.

**Correct implementation:** Rename tests to the standard pattern, for example `addFeedbackItem_whenRetrospectiveIsClosed_throwsException`.

### 10. Tests do not consistently use explicit Arrange-Act-Assert comments

**Location:** `src/test/java/com/stackcraft/retroflow/ServiceMutationTest.java:63-74, 76-88, 91-99` and `src/test/java/com/stackcraft/retroflow/RetroflowBusinessRulesMatrixTest.java:52-58`.

**Standard — §3.3:** “All tests must use the AAA pattern with explicit comments.” Violation severity: **Minor**.

**Why it matters:** These tests arrange fixtures, perform operations, and assert outcomes without the required explicit phase comments, making the test flow less clear.

**Correct implementation:** Organize each test into Arrange, Act, and Assert phases and mark each phase with the required comments.

### 11. Test data contains unexplained numeric literals

**Location:** `src/test/java/com/stackcraft/retroflow/ServiceMutationTest.java:53, 57, 103-105, 117, 154, 226`.

**Standard — §3.4:** “Test data must use named constants or clearly named local variables.” Violation severity: **Minor**.

**Why it matters:** IDs such as `1L`, `2L`, and `3L` are used directly in fixture setup and action-item creation without names that explain their test role.

**Correct implementation:** Replace these literals with clearly named constants or local variables, such as `TEAM_ID`, `RETROSPECTIVE_ID`, and `HIGH_PRIORITY_ACTION_ID`.

### 12. README instructions do not accurately describe how to run this project

**Location:** `README.md:10-11, 15-23, 27-31, 45-46`.

**Standard — §6.2:** The README must include “How to get it running locally (exact commands, not ‘follow standard setup’),” “How to run the tests,” and “A link to the API documentation or a brief endpoint list.” Violation severity: **Minor**.

**Why it matters:** The README specifies Gradle commands and PostgreSQL configuration, but this repository contains a Maven `pom.xml`, no Gradle wrapper, and an H2 datasource configuration. It also points readers to the controller rather than providing an endpoint list or API-documentation link.

**Correct implementation:** Document the actual H2/Maven setup and runnable Maven commands, and include a concise endpoint list or a working API-documentation link.

## Scope note

The layered package structure was not reported as a violation: §1.1 explicitly grandfathers existing projects, and the project brief describes this repository as an inherited, partially delivered codebase.
