# Resort Booking System

A booking system for a small Philippine resort. Guests search availability and book online; staff manage
reservations. Built with Spring Boot 4, Hibernate and PostgreSQL, with server-rendered Thymeleaf pages and a
parallel JSON API secured by JWT.

The central problem it solves is **making a double-booking impossible**, not merely unlikely — see
[Concurrency](#concurrency-the-interesting-part).

## Running it

**Prerequisites:** JDK 21+, PostgreSQL 14+ (tested on 18). No Docker required.

```bash
# 1. Create the database and an application role
psql -U postgres -c "CREATE ROLE resort LOGIN PASSWORD 'resort';"
psql -U postgres -c "CREATE DATABASE resortbooking OWNER resort;"

# 2. Local config — this folder is git-ignored
mkdir -p config
cat > config/application.properties <<'EOF'
app.jwt.secret=local-dev-signing-key-at-least-32-bytes-long
app.admin.email=admin@resort.test
app.admin.password=admin12345
EOF

# 3. Run
./mvnw spring-boot:run
```

Flyway creates the schema and seed data on first start. Open <http://localhost:8080>.

The JWT secret has **no default on purpose** — the app refuses to start without one, so a signing key can never
be accidentally shipped from source control. In production it comes from the `JWT_SECRET` environment variable.

A guest can ask to become staff from their own account (`/me/staff-request`); an admin grants it from
`/admin/staff-requests`. There's still no admin UI for creating an account directly or for granting `ADMIN`
itself - that stays a manual step, on purpose (see the business rules table below):

```sql
UPDATE app_user SET role = 'ADMIN' WHERE email = 'someone@example.com';
```

## What works

| Area | Status |
|---|---|
| Guest booking flow in the browser | search → rooms → details → review → confirm → my bookings → cancel |
| Staff room calendar | weekly grid, colour-coded by status, links to booking detail |
| Staff booking actions | confirm / check-in / check-out / cancel, each a POST guarded by BR-08 |
| Check-in approval workflow | front desk can only check in on the actual check-in date; early/late attempts file a request an admin approves or rejects |
| Staff access requests | a guest can ask to become front desk; an admin approves or rejects it |
| Registration and session login | Thymeleaf pages, CSRF protected |
| JSON API under `/api/v1` | availability, bookings, rooms, room types, auth |
| JWT auth with roles | `GUEST`, `FRONT_DESK`, `ADMIN` |
| Schema as migrations | Flyway, with constraints enforcing the business rules |
| Page-level error handling | bad input redirects to a flash message, not a stack trace; a 404 renders a real page instead of forcing login |

**Not built yet:** `/staff/bookings` search page, admin rooms/rates pages, seasonal-rate XML import/export,
booking edit (`PATCH`), notification microservice, Docker and CI. The staff pages also reuse the guest layout
rather than the dark sidebar in the UI brief — functional, not yet matching the visual spec.

## Architecture

```
com.resortapi.resortbooking
├── config/      security chains, JWT filter, admin bootstrap
├── controller/  page controllers (@Controller) and REST controllers (@RestController)
├── dto/         records for the API, mutable form objects for pages
├── entity/      JPA entities and their enums
├── exception/   custom exceptions + the JSON problem handler
├── repository/  Spring Data JPA
└── service/     business rules and transaction boundaries
```

Requests flow **controller → service → repository**. Entities never reach a template or a JSON response;
controllers receive DTOs. `spring.jpa.open-in-view=false`, so all loading happens inside the service
transaction — a lazy association touched later fails loudly in a test rather than silently issuing extra
queries in production.

**Two security chains, deliberately different:**

| | `/api/**` (order 1) | everything else (order 2) |
|---|---|---|
| Auth | JWT bearer token | session + form login |
| Sessions | stateless | yes |
| CSRF | disabled | **enabled** |

CSRF is off for the API because a bearer token is not attached automatically by the browser, so a malicious
site cannot replay it. Session cookies *are* sent automatically, so the page chain keeps CSRF on.

## Business rules

| ID | Rule | Enforced by |
|---|---|---|
| BR-01 | No overlapping active bookings for a room | DB exclusion constraint + service lock |
| BR-02 | Check-out is exclusive (Oct 12→14 is 2 nights) | `daterange` half-open semantics |
| BR-03 | Check-in today or later, max 30 nights | `BookingDateRules` |
| BR-04 | Guests cannot exceed room capacity | `RoomType.accommodates` |
| BR-05/06 | Seasonal rate per night, total frozen at booking | `PricingService` |
| BR-07 | Money is `BigDecimal`, never floating point | entity + `NUMERIC(10,2)` |
| BR-08 | Only legal status transitions | `BookingStatus.canMoveTo` |
| BR-09 | Guests cancel only their own, before check-in | `BookingService` + state machine |
| BR-10 | Maintenance rooms are unbookable | availability query |
| BR-11 | References never expose row ids | `booking_ref_seq` |
| *(added)* | Front desk can only check in on the booking's actual date; off-date attempts require admin approval. Admin's own check-in is never gated — their role is the override. | `BookingService.checkIn` + `CheckInApprovalService` |
| *(added)* | A guest becomes staff only through an admin-approved request, never by self-registration. Escalating to `ADMIN` itself is never reachable this way — only `FRONT_DESK`, and only by a direct database action. | `StaffRequestService` + `StaffRequest.approve` |

Neither of those last two rules is in the original PRD (BR-01–BR-11) — both were added afterward as real
product requirements. Each gets its own table, its own tiny state machine (`PENDING → APPROVED`/`REJECTED`,
the same shape as `BookingStatus`), and its own admin queue (`/admin/checkin-requests`,
`/admin/staff-requests`).

## Concurrency: the interesting part

Two guests clicking "Book" on the last room at the same instant must produce exactly one booking (NFR-02).

The service locks candidate rooms with `SELECT … FOR UPDATE` before checking overlap. **That alone is not
enough.** A row lock can only lock rows that exist — if two transactions both run the availability query
before either inserts, both see the room as free. Under `READ COMMITTED` that gap is real.

So the database has the final say:

```sql
CONSTRAINT no_overlap EXCLUDE USING gist (
    room_id WITH =, daterange(check_in, check_out) WITH &&
) WHERE (status <> 'CANCELLED')
```

A 40-way concurrent test against one room yields **1 × `201` and 39 × `409`**, with exactly one row stored.
One of those 409s comes from the constraint rather than the lock — `BookingService` catches
`DataIntegrityViolationException` and translates it into the same clean 409 a user would get any other way.

The `WHERE (status <> 'CANCELLED')` clause makes it a *partial* constraint: cancelled bookings keep their row
for audit but stop blocking the dates.

## Security notes

- Passwords are BCrypt-hashed; the first admin is created by `AdminAccountInitializer` from environment
  variables, never from a migration.
- Requesting **someone else's** booking returns **404, not 403**. A 403 would confirm the reference exists and
  let an attacker enumerate bookings.
- A guest's booking is always recorded against the identity in their token; guest details in the request body
  are ignored for non-staff callers. Staff keep that path, because front desk books for walk-ins.
- BR-09 ("guests cancel only before check-in") needs no extra check in `BookingService` — `CANCELLED` is only
  reachable from `PENDING` or `CONFIRMED` in `BookingStatus.canMoveTo`, so a `CHECKED_IN` booking already
  cannot be cancelled by anyone, guest or staff.
- `/error` is `permitAll` on the page security chain. Miss this and *any* unhandled failure — even a plain
  404 for an anonymous visitor — forwards internally to `/error`, which then demands a login before it can
  render, turning a mistyped URL into a confusing redirect.

## Testing

```bash
psql -U postgres -c "CREATE DATABASE resortbooking_test OWNER resort;"
./mvnw test
```

| Layer | Tool | Covers |
|---|---|---|
| Unit | JUnit 5, Mockito | BR-02…BR-08, pricing, date rules — no Spring, no database |
| Integration | `@SpringBootTest` + real PostgreSQL | the overlap query, exclusion constraint |
| API | MockMvc through the real security chain | roles, 401/403/404, validation |

Tests use a **separate database** so they can never read or destroy development data, and each runs in a
transaction that rolls back. The spec calls for Testcontainers; this uses a local database instead because
Docker was not available on the development machine — swapping back is a small change.

## API

| Method | Path | Role |
|---|---|---|
| `POST` | `/api/v1/auth/register`, `/api/v1/auth/login` | public |
| `GET` | `/api/v1/availability` | public |
| `POST` | `/api/v1/bookings` | authenticated |
| `GET` | `/api/v1/bookings/{reference}` | owner or staff |
| `POST` | `/api/v1/bookings/{reference}/cancel` | owner or staff |
| `POST` | `/api/v1/bookings/{reference}/confirm`, `/check-in`, `/check-out` | staff |
| `GET`/`POST` | `/api/v1/rooms`, `/api/v1/room-types` | staff / admin |
| `GET` | `/api/v1/checkin-requests` | admin |
| `POST` | `/api/v1/checkin-requests/{id}/approve`, `/reject` | admin |
| `POST` | `/api/v1/staff-requests` | authenticated |
| `GET` | `/api/v1/staff-requests` | admin |
| `POST` | `/api/v1/staff-requests/{id}/approve`, `/reject` | admin |

Errors use RFC 7807 `ProblemDetail` with a stable `code`:

```json
{
  "title": "Room not available",
  "status": 409,
  "detail": "No Deluxe room is free from 2026-10-12 to 2026-10-14",
  "code": "ROOM_NOT_AVAILABLE"
}
```

## Notable implementation choices

- **`@ManyToOne` is explicitly `LAZY`** — JPA defaults it to `EAGER`, which quietly turns a room list into
  N+1 queries. List queries use `@EntityGraph` to fetch what the page needs in one round trip.
- **`@Enumerated(STRING)`**, never the `ORDINAL` default: reordering an enum would otherwise silently
  change the meaning of every existing row.
- **No Lombok on entities.** `@Data` generates `equals`/`hashCode` across all fields, which triggers lazy
  loads and misbehaves against Hibernate proxies.
- **Status changes live on the entity** (`booking.cancel()`), so the BR-08 state machine has one home and is
  unit-testable without Spring or a database.
- **`@Transactional(noRollbackFor = ...)` on the check-in approval path, in two places.** When front desk
  checks in on the wrong date, the service must both *save* a `CheckInRequest` row and *throw*, so the caller
  gets a clear error. Spring's default is to roll back the whole transaction on any unchecked exception —
  which silently discarded the save along with everything else, verified by querying the table directly and
  finding it empty despite the API reporting "request sent." The exemption has to be on **both**
  `CheckInApprovalService.fileOrReportPending` and the outer `BookingService.checkIn` that calls it, since
  rollback-only is a one-way flag: either proxy boundary applying its default rule re-marks the shared
  transaction, regardless of what the other one decided. `CheckInApprovalIntegrationTest` is deliberately
  **not** `@Transactional` (unlike the other integration tests) to catch this class of bug at all — a shared
  test transaction never commits or rolls back mid-test, so a flushed-but-doomed row reads back fine and the
  regression passes unnoticed.
# resortbooking
