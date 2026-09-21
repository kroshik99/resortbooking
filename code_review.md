# Code Review — Resort Booking System

Full-codebase review, not a diff review. Every finding below was verified against the actual running
application or the actual source — not inferred from pattern-matching alone. Where a finding was proven by
reproducing it live, that's stated explicitly; where it's proven by code inspection, the exact file/line is
given so it can be checked directly.

One issue found during this review (a misleading notification badge introduced in the nav reorganization)
was small and unambiguous enough to fix immediately rather than list as pending; it's noted at the bottom
under "Fixed during this review" rather than in the tables below.

---

## Critical

### C-1. Registering with a walk-in guest's email silently takes over their booking history — including the ability to cancel their reservation

**Where:** `src/main/java/com/resortapi/resortbooking/service/AuthService.java:49-53`

```java
guests.findByEmailIgnoreCase(request.email())
        .ifPresentOrElse(
                existing -> existing.linkTo(user),
                () -> guests.save(new Guest(request.fullName(), request.email(), request.phone(), user)));
```

**What's wrong:** When front desk books a walk-in guest, a `Guest` row is created with no linked `AppUser`
(FR: staff can book for someone who has no account). The intent of the code above is reasonable: if that
same person later registers for real, link their new account to their existing guest record so they can see
their past stay. But nothing verifies the registrant *is* that person — it only checks that the email string
matches. Registration itself (`POST /api/v1/auth/register`) is `permitAll` and requires no proof of mailbox
ownership (there is no verification email — this app has no email-sending capability at all).

**Verified live**, end to end, in this session:
1. As admin, booked a walk-in: `POST /api/v1/bookings` with guest email `victim-walkin@example.com` — no
   `app_user` row existed for that email. → booking `RB-2026-00054` created.
2. As an anonymous caller, `POST /api/v1/auth/register` with that exact email and an attacker-chosen
   password — succeeded with `201`, no verification step.
3. Logged in as the attacker and called `GET /api/v1/bookings/RB-2026-00054` — the victim's real booking
   (room, dates, price) was returned in full.
4. `POST /api/v1/bookings/RB-2026-00054/cancel` as the attacker — **succeeded**, booking flipped to
   `CANCELLED`.

This isn't a theoretical race or an edge case — it's fully deterministic and requires nothing but knowing a
real guest's email address (something front desk would have typed in from a phone booking, walk-in form,
etc.). Impact is both confidentiality (full stay history — dates, room, price) and integrity/availability
(a real, previously-legitimate booking can be cancelled by a third party).

**Scope check:** this is the only place in the codebase where an *unverified* self-registration email is used
to attach to pre-existing data. Every other `findByEmailIgnoreCase` call (grepped across the whole `service`
package) resolves the *already-authenticated* caller's own email, which is safe because Spring Security has
already verified the password for that identity.

**Suggested direction (not implemented — a review, not a fix):** don't auto-link on registration at all;
instead require some proof of the relationship before linking (e.g., the registrant supplies a real booking
reference for that email, or the link only happens after front desk confirms it). Simplest immediate
mitigation: stop auto-linking silently and instead flag the account for admin confirmation before it can see
the prior guest's bookings.

---

## High

### H-1. No automated test coverage for most of the newest features — the state machines are unit-tested, the HTTP/security/binding layer around them isn't

**Where:** `StaffRequestController`, `StaffRequestPageController`, `BookingController.reschedule` +
`StaffPageController`'s edit/search additions, `AdminCatalogPageController` (rooms and room types pages) —
none of these have integration or API-level tests.

**What exists:** `StaffRequestTest` and the new `BookingTest` reschedule cases correctly cover the entity
state machines (`StaffRequest.approve/reject`, `Booking.reschedule`) in isolation — no Spring context, no
database. That's good, necessary coverage, but it stops at the entity boundary.

**What's missing:** everything above the entity — role enforcement (`hasRole('ADMIN')` on staff-request
review, ownership checks on booking reschedule via `requireVisibleTo`), form binding, CSRF, the actual
HTTP status/JSON shape a client gets back, and the page-controller flash-message/redirect behavior. All of
this was verified manually with `curl` during this session (registering, logging in, submitting forms,
checking the database) — but none of that verification is captured as a test that runs on `mvnw test` or
would catch a regression. Compare to `CheckInApprovalIntegrationTest`, which exists specifically *because*
manual verification once missed a real bug (the `noRollbackFor` issue) that only a real, non-`@Transactional`
integration test could catch. The same class of risk now exists, untested, for every feature built this
session.

**Suggested direction:** at minimum, one `MockMvc`-based integration test per new controller mirroring
`BookingApiIntegrationTest`'s shape — role-gating (403 for the wrong role), the happy path, and the
ownership/ownership-denial path for reschedule.

### H-2. Sending a room to maintenance doesn't check for existing future bookings on it

**Where:** `src/main/java/com/resortapi/resortbooking/service/RoomService.java:48-58`

```java
public RoomDto updateStatus(Long id, RoomStatus status) {
    Room room = rooms.findWithRoomTypeById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Room", id));
    switch (status) {
        case AVAILABLE -> room.returnToService();
        case MAINTENANCE -> room.sendForMaintenance();
    }
    return RoomDto.from(room);
}
```

**What's wrong:** `MAINTENANCE` correctly removes a room from future availability searches (BR-10 — verified
by reading `RoomRepository.FREE_ROOMS`, which filters `status = AVAILABLE`), but nothing here checks whether
the room already has a `CONFIRMED` or `PENDING` booking starting after today. An admin can send a room under
maintenance with a guest already booked into it next week, with **zero warning** from the system. The guest
would show up (or front desk would discover it at check-in time) with no record anywhere that this conflict
was ever flagged. This is a realistic operational scenario for a resort, not a contrived one — rooms
genuinely need unscheduled maintenance, and the whole point of this system (per its own README) is making
booking conflicts impossible, not just for the initial reservation.

**Suggested direction:** before applying `MAINTENANCE`, query for active (`PENDING`/`CONFIRMED`/`CHECKED_IN`)
bookings on that room with `checkOut > today`, and either block the status change or require the admin to
acknowledge the conflict (the same UX pattern already used for early check-in requests would fit well here).

---

## Low

### L-1. Admin room/room-type creation and rename don't catch the concurrent-duplicate race the way bookings and staff requests do

**Where:** `AdminCatalogPageController.createRoomType`, `.updateRoomType`, `.createRoom` (all in
`src/main/java/com/resortapi/resortbooking/controller/AdminCatalogPageController.java`)

Each only catches `DuplicateResourceException` (the pre-check race loser). Two admins submitting the same
new room-type name or room number at the exact same instant would have the second one hit the database's
own `UNIQUE` constraint directly, throwing `DataIntegrityViolationException` — which nothing here catches,
and which `AdminCatalogPageController` isn't registered with `PageExceptionHandler` to catch either. The
result is a fall-through to the generic `/error` page instead of a clean "that name is taken" message.
`BookingService.create()` and `StaffRequestService.request()` both explicitly handle this same class of race
for their own unique constraints — this is the one place that pattern wasn't carried over. Low severity only
because it requires two admins racing an identical, rare, low-frequency action.

### L-2. `AppUser.enabled` is fully wired for reading but has no write path anywhere

**Where:** `entity/AppUser.java:31` (field, defaults `true`), `service/AppUserDetailsService.java:38`
(`.disabled(!user.isEnabled())` — correctly feeds Spring Security).

There is no setter, no admin action, and no API endpoint that ever sets this to `false`. It's a complete,
correctly-wired account-disable mechanism with no way to actually use it. Not a bug — nothing breaks — just
worth knowing this isn't a real feature yet if it's ever assumed to be.

### L-3. The JWT's `role` claim is written but never read back

**Where:** `service/JwtService.java:36` writes `.claim("role", user.getRole().name())`;
`config/JwtAuthenticationFilter.java` only ever reads `claims.getSubject()` (grepped the whole codebase for
any other read of this claim — none exists). Authorization correctly re-derives the current role from the
database on every request instead, which is actually the *safer* design (a tampered or stale claim can't
grant privilege) — but the claim itself is dead data. Worth a one-line comment if kept, or removing it.

### L-4. `description` fields on room types have no length cap

**Where:** `dto/CreateRoomTypeRequest.java` and the new `dto/RoomTypeForm.java` — every other free-text
field in the app has a `@Size` bound (e.g. `RegisterForm.fullName` capped at 100) except this one, which maps
to an unbounded `TEXT` column. Low risk since only `ADMIN` can reach this input.

### L-5. `AvailabilityService.search()` issues two extra queries per room type per search

**Where:** `service/AvailabilityService.java:47-73` — for each room type, one `findFreeRooms` query plus one
seasonal-rate lookup inside `PricingService.quote()`. At this app's actual scale (4 seeded room types) this
is inconsequential; flagged only because it's the one search path that doesn't batch the way
`CalendarService.week()` and `BookingRepository.findForCalendar` do (both fetch everything needed in one
query).

---

## Fixed during this review

While reviewing the navigation reorganization from the previous change, found and immediately fixed: the
"Admin settings" top-nav link (added to replace "Staff area" for admin accounts) had inherited the combined
check-in-approval/staff-request pending-count badge, but now points to `/admin/rooms` — a page with no
relationship to either count. Clicking a badged "Admin settings [3]" landed on room inventory, not whatever
had 3 pending items. The badge has been removed from that link; the sidebar (`layout/staff.html`) already
shows each count correctly badged on its own specific page (`/admin/checkin-requests`,
`/admin/staff-requests`).
