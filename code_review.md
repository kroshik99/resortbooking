# Code Review — Resort Booking System

Full-codebase review, not a diff review. Every finding was verified against the actual running application or
the actual source — not inferred from pattern-matching alone. Where a finding was proven by reproducing it
live, that's stated explicitly; where it's proven by code inspection, the exact file/line is given so it can
be checked directly.

**Status as of the follow-up pass:** every Critical and High finding is fixed, verified live, and locked in
with a regression test. One High item (test coverage) is fixed to a meaningful, verified extent but not
exhaustively — see H-1's status note for exactly what is and isn't covered; that's the one item not "100%"
done. Three of five Low items are fixed; two are explicitly left as acknowledged, accepted gaps with reasons
given, not silently skipped.

| ID | Severity | Status |
|---|---|---|
| C-1 | Critical | ✅ Fixed, regression test passing |
| H-1 | High | ⚠️ Meaningfully improved, not exhaustive — see status note |
| H-2 | High | ✅ Fixed, regression test passing, verified live through the real admin page |
| L-1 | Low | ✅ Fixed |
| L-2 | Low | ⏸ Acknowledged, not built (out of scope — see note) |
| L-3 | Low | ✅ Fixed |
| L-4 | Low | ✅ Fixed |
| L-5 | Low | ⏸ Acknowledged, not built (genuinely inconsequential at this app's scale) |

---

## Critical

### C-1. Registering with a walk-in guest's email silently takes over their booking history — including the ability to cancel their reservation

**Status: ✅ Fixed.** `AuthService.register()` no longer auto-links. If the email already belongs to an
existing `Guest` record (a walk-in with no login), registration is refused with a clear message instead of
silently taking it over. Front desk links the two manually after confirming identity in person — documented
in `README.md` with the exact SQL, the same pattern already used for granting `ADMIN`.

**Where:** `src/main/java/com/resortapi/resortbooking/service/AuthService.java`

**What was wrong:** When front desk books a walk-in guest, a `Guest` row is created with no linked `AppUser`.
The original code's intent was reasonable — if that same person later registers for real, link their new
account to their existing guest record so they can see their past stay — but nothing verified the registrant
*was* that person, only that the email string matched. Registration (`POST /api/v1/auth/register`) is
`permitAll` and this app has no email-sending capability, so there was no way to prove mailbox ownership.

**Verified live**, end to end, before the fix:
1. As admin, booked a walk-in with guest email `victim-walkin@example.com` — no `app_user` row existed for
   that email. → booking `RB-2026-00054` created.
2. As an anonymous caller, registered with that exact email and an attacker-chosen password — succeeded with
   `201`, no verification step.
3. Logged in as the attacker and read the victim's real booking in full (room, dates, price).
4. Cancelled it as the attacker — **succeeded**.

Confirmed fixed by the same sequence, now producing a `409 DUPLICATE_RESOURCE` at step 2 and no account ever
existing for the attacker to log into. Locked in as
`BookingApiIntegrationTest.registrationRefusesToClaimAWalkInsEmail`, which reproduces the full attack sequence
and asserts it's blocked at every stage.

---

## High

### H-1. No automated test coverage for most of the newest features

**Status: ⚠️ Meaningfully improved, not exhaustive.** 11 new integration tests added across three files,
covering the highest-value gaps first (security and correctness, not every form and button):

- `StaffRequestApiIntegrationTest` (4 tests) — a guest can request, a duplicate request is rejected, only
  `ADMIN` may list/approve/reject, and approval actually promotes the account (verified by the promoted
  account immediately passing a `FRONT_DESK`-gated endpoint with no re-login, proving the JWT filter's
  re-derive-role-from-database behavior).
- `BookingApiIntegrationTest` (+5 tests) — reschedule's happy path, its ownership check (404 for a
  non-owner, same rule as everywhere else), its capacity revalidation, plus the C-1 and H-2 regression tests.
- `AdminPageSecurityIntegrationTest` (4 tests) — every `/admin/**` and `/staff/**` page route, confirming
  anonymous → redirect, `GUEST` → 403, `FRONT_DESK` → staff pages only, `ADMIN` → everything. This required
  adding `spring-security-test` as a new test dependency and building `MockMvc` explicitly with
  `SecurityMockMvcConfigurers.springSecurity()` — on this project's Spring Boot 4 / Spring Security 7
  combination, `@AutoConfigureMockMvc` alone did not wire in `@WithMockUser` support; requests were silently
  treated as anonymous until that was added explicitly. Worth knowing if more page-security tests are added
  later.

**What's still not covered**, so this isn't "100%": the actual POST/form-submission behavior of
`AdminCatalogPageController` (create/edit room and room-type, beyond the GET role-gating now tested) and
`StaffRequestPageController`'s page-level POST flow (only its REST API twin is tested). Those were verified
manually via curl/PowerShell during development but, same as before, that verification doesn't run on
`mvnw test`. Extending the pattern above to those two would close the remaining gap.

### H-2. Sending a room to maintenance doesn't check for existing future bookings on it

**Status: ✅ Fixed.** `RoomService.updateStatus()` now checks for any `PENDING`/`CONFIRMED`/`CHECKED_IN`
booking on that room with `checkOut` after today before allowing `MAINTENANCE`, and refuses with a clear
`RoomHasActiveBookingsException` (409 `ROOM_HAS_ACTIVE_BOOKINGS` on the API, a flash error on the admin page)
if one exists.

**Where:** `src/main/java/com/resortapi/resortbooking/service/RoomService.java`,
new `BookingRepository.existsActiveBookingAfter`, new `RoomHasActiveBookingsException`.

**What was wrong:** nothing prevented an admin from sending a room to maintenance while a guest already had a
confirmed reservation there, with zero warning anywhere in the system.

**Verified live** through the real admin page, not just the API: booked a room via the API, then submitted
the actual "Send for maintenance" form at `/admin/rooms` as logged-in admin — got the flash error "Room 101
has an active booking that hasn't checked out yet, so it cannot be sent for maintenance," and confirmed via
direct database query that the room's status never changed. Locked in as
`BookingApiIntegrationTest.maintenanceRefusedWhileRoomHasAnActiveBooking`.

---

## Low

### L-1. Admin room/room-type creation and rename didn't catch the concurrent-duplicate race

**Status: ✅ Fixed.** `AdminCatalogPageController.createRoomType`, `.updateRoomType`, and `.createRoom` now
also catch `DataIntegrityViolationException` alongside `DuplicateResourceException`, matching the pattern
`BookingService.create()` and `StaffRequestService.request()` already used for their own unique-constraint
races.

### L-2. `AppUser.enabled` is fully wired for reading but has no write path anywhere

**Status: ⏸ Acknowledged, not built.** This was already correctly categorized as "not a bug" in the original
finding — an account-disable admin feature was never requested and building one wasn't part of this pass.
Left as-is; still worth knowing this isn't a real feature if it's ever assumed to be.

### L-3. The JWT's `role` claim was written but never read back

**Status: ✅ Fixed.** Removed from `JwtService.issue()`. `JwtAuthenticationFilter` already re-derived the
role from the database on every request via the subject claim alone, so nothing else changed.

### L-4. `description` fields on room types had no length cap

**Status: ✅ Fixed.** Added `@Size(max = 1000)` to `CreateRoomTypeRequest.description` and
`RoomTypeForm.description`, matching every other free-text field in the app, plus the matching
`field-error` display in both admin templates.

### L-5. `AvailabilityService.search()` issues two extra queries per room type per search

**Status: ⏸ Acknowledged, not built.** Confirmed still true and still genuinely inconsequential at this
app's actual scale (4 seeded room types → at most ~9 queries per search). Not worth the added complexity of
batching for zero real-world benefit at this size; noted here rather than fixed for its own sake.

---

## Also done in this pass: room types/rooms pages redesigned for scale

Not from the review, but done alongside it: `/admin/room-types` and `/admin/rooms` were stacked cards that
would become unwieldy well before real "many room types" scale. Both are now data tables (name/capacity/
price/description with an Edit link; room/type/status with the maintenance-toggle action), with the add-form
collapsed behind a `<details>` disclosure instead of always sitting open beneath the list. New CSS is scoped
under "admin data tables" in `app.css` and doesn't touch anything the guest-facing pages use.

---

## Fixed earlier in this project (for reference)

While reviewing the navigation reorganization from an earlier change, found and immediately fixed: the
"Admin settings" top-nav link had inherited a combined check-in-approval/staff-request pending-count badge
from the link it replaced, but points to `/admin/rooms` — unrelated to either count. The badge was removed;
the sidebar (`layout/staff.html`) already shows each count correctly badged on its own specific page.
