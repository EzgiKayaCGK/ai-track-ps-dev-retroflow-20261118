# RetroFlow

Backend API for StackCraft's internal retrospective tool. Replaces the mix of sticky notes,
Miro boards, and shared Google Docs the 12 dev teams currently use to run retros.

## Tech stack

- Java 21
- Spring Boot 3
- Maven
- In-memory H2

## Getting started

1. Install Java 21 and Maven.
2. Run the app:

   ```
   mvn spring-boot:run
   ```

3. The API will be available at http://localhost:8080. Data is lost when the app stops.

## Running tests

```
mvn test
```

The inherited business-rule tests currently reference service methods and signatures that
are not implemented yet, so the complete test suite does not compile.

## Project structure

Standard Spring Boot layered structure — controllers, services, repositories, entities.

## Current status

Core domain model (Team, Retrospective, FeedbackItem, ActionItem) and CRUD endpoints are in
progress. The following controller routes have been added, but their service methods are
stubs: valid requests currently return `501 Not Implemented` until the service layer is
implemented.

All routes require an `X-User-Name` header containing a nonblank name of at most 100
characters. Errors use the JSON shape `{"message":"..."}`.

| Method and path | Request body | Success response |
| --- | --- | --- |
| `POST /api/teams` | `{"name":"Team","members":["Alice"]}` | `201`, team |
| `GET /api/teams/{id}` | None | `200`, team |
| `POST /api/teams/{teamId}/retrospectives` | `{"title":"Sprint retro"}` | `201`, retrospective |
| `GET /api/teams/{teamId}/retrospectives` | None | `200`, array of retrospectives |
| `PUT /api/retrospectives/{id}/close` | None | `200`, retrospective |

Team responses contain `id`, `name`, and `members`. Retrospective responses contain `id`,
`teamId`, `title`, `date`, and `status`. Invalid input is reported as `400`; service
implementations should throw `ResourceNotFoundException` for `404`, `ForbiddenException`
for `403`, `ConflictException` for `409`, and `RetroflowException` for business rules
that require `400`.

## Contact

The previous team lead has left the company, so there is no forwarding contact. Ping #retroflow-support on
Slack if you get stuck, though response times have been slow lately.
