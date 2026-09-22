# Frontend Modernization — React + Tailwind

Planning notes for moving off the server-rendered Thymeleaf pages toward a React + Tailwind frontend with
proper animation. This exists to record what the current backend actually gives you and what it doesn't, so
the new frontend gets built against reality rather than assumptions made from scratch. The decisions below
marked "not decided here" are yours to make — this file lays out the tradeoffs, not the answer.

## What the backend already gives you for free

- A complete, separately-secured JSON API at `/api/v1/**` — stateless JWT bearer auth, CSRF already
  **disabled** on this chain (correct as-is for a token-based SPA; no backend change needed there).
- Every guest action already has a REST endpoint: search availability, book, cancel, reschedule
  (`PATCH /api/v1/bookings/{ref}`), register, login.
- Most staff/admin actions do too: confirm/check-in/check-out, room and room-type CRUD, staff-access
  requests, check-in approvals — see the API table in `README.md` for the full list.
- Role model (`GUEST` / `FRONT_DESK` / `ADMIN`) is enforced **server-side**, per endpoint, already. React
  doesn't need to reimplement authorization — only reflect it in the UI (hide/disable what a role can't do).
  The server remains the real guard regardless of what the UI shows.

## What does NOT carry over

- Session + CSRF + Thymeleaf rendering is a **separate security chain** (`pageSecurity` in `SecurityConfig`)
  from the API. A React app talking only to `/api/v1/**` never touches it — pure JWT, full stop.
- Server-side redirects, flash messages (`RedirectAttributes`), and Post/Redirect/Get all disappear. React
  owns navigation and needs its own success/error UI (toasts, inline banners) instead of a flash attribute
  that survives a redirect.
- A few staff/admin page controllers call services **directly**, bypassing their own REST controllers —
  e.g. `AdminCatalogPageController` calls `RoomTypeService` rather than going through `RoomTypeController`.
  Functionally identical, but it means "the page can do X" doesn't guarantee "there's a REST endpoint for X"
  — check before assuming parity.

## Known API gaps if going fully headless

- `DashboardService.today()` is only ever rendered server-side into the dashboard template — there's no
  `GET /api/v1/dashboard` (or similar) returning those stats as JSON yet. Needed before the dashboard can
  move to React.
- Same story for the staff-request page flow and the admin rooms/room-types CRUD — the REST twins mostly
  exist already (`StaffRequestController`, `RoomController`, `RoomTypeController`) but haven't been audited
  end-to-end against what the page controllers actually do. Worth a pass before relying on them.

## Two realistic paths

1. **Incremental.** Keep Thymeleaf serving the guest-facing pages (simpler, no auth model change, arguably
   still worth it for SEO on the public search/room pages). Build the **staff/admin side** in React against
   `/api/v1/**` first — staff and admin already require login and get zero SEO benefit from server rendering,
   so they're the natural first slice to convert, and it's the smaller, more contained surface.
2. **Full replacement.** React consumes the API exclusively. Every `*PageController` and Thymeleaf template
   gets retired. Session auth goes away entirely — JWT only, with a deliberate choice about where the token
   lives client-side (see below). Render serves the built React app as static assets, either bundled into
   the same Spring Boot service or deployed separately.

Neither is inherently correct — the tradeoff is upfront rewrite cost (2) against running two rendering paths
side by side for a while (1). Worth deciding explicitly rather than drifting into a half-migrated state where
some pages are React and some are Thymeleaf with no plan for the rest.

## Animation

Framer Motion is the standard pairing with React for this kind of "smooth experience" ask — page transitions,
list reordering, modal/drawer enter-exit, the calendar bars animating into place. Tailwind's own utilities
(`transition`, `duration-*`, `ease-*`) cover simpler hover/focus states without pulling in a library at all.
Rule of thumb: Tailwind transition classes for hover/focus micro-interactions, Framer Motion for anything
with real motion — mount/unmount, reordering, drag. Using both isn't overkill; using Framer Motion for a
button hover is.

## Not decided here — your call

- Full rewrite (path 2) vs. incremental (path 1).
- Whether guest-facing pages stay server-rendered for SEO, or move to React too (with SSR/SSG if SEO still
  matters, e.g. Next.js instead of plain React).
- Where the JWT lives client-side — memory, `localStorage`, or an `httpOnly` cookie. Each has a different
  XSS/CSRF tradeoff; picking one deliberately matters more than picking fast.
- Deployment shape: one Spring Boot service serving the built React app as static resources, versus two
  separately deployed services (adds CORS configuration to `SecurityConfig`, currently unconfigured since
  everything is same-origin today).

## Using `design-system.md` alongside this

`design-system.md` is the source of truth for colors/typography/components as you redesign them. When
Tailwind setup actually starts, its token table becomes the starting point for `tailwind.config.js`'s
`theme.extend.colors` — translate `--primary: #1B4DB1` etc. directly rather than re-deriving a new palette
from scratch, unless the redesign specifically calls for new colors.
