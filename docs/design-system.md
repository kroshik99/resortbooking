# Design System

This documents the design **as actually implemented** in `src/main/resources/static/css/app.css` — not an
aspirational spec. It exists so new colors, typography, and components can be planned here first, then
carried into code (today: the CSS file directly; going forward: a Tailwind config — see
`frontend-modernization.md` for that transition).

**This is your file to edit.** Change colors, add new component ideas, cross things out — treat it as the
working reference for what the app *should* look like, separate from what currently implements it.

---

## Design tokens

Defined as CSS custom properties in `app.css`'s `:root` block.

| Token | Value | Use |
|---|---|---|
| `--font` | `"IBM Plex Sans", system-ui, sans-serif` | All text |
| `--font-mono` | `"IBM Plex Mono", ui-monospace, monospace` | Booking references, room numbers, prices, stat numbers |
| `--bg` | `#F4F5F7` | Page background |
| `--surface` | `#FFFFFF` | Cards, forms, top bar, table headers' row background is `--bg` instead |
| `--ink` | `#16181D` | Main text |
| `--muted` | `#5B6270` | Labels, secondary text, table headers |
| `--border` | `#E1E4EA` | Card/input/table borders |
| `--primary` | `#1B4DB1` | Links, buttons, focus outline, stat numbers, hover states |
| `--primary-soft` | `#EEF3FC` | Selected/today calendar cell, table row hover |
| `--sidebar` | `#1C2421` | Staff/admin sidebar background only |
| `--danger` | `#B42318` | Errors, cancel/destructive buttons, badges |

**Known gap:** IBM Plex Sans/Mono are referenced but never actually loaded — there's no `<link>` to Google
Fonts (or any font host) anywhere in the templates. Every page currently renders in the `system-ui` /
`ui-monospace` fallback. Worth a deliberate decision: actually load IBM Plex, or update the token to name
whatever font is really being used.

Spacing scale in practice: 4, 8, 12, 16, 24, 32px. Radius: 12px for cards/panels, 8px for inputs/buttons,
999px for pills (chips, badges, the count-chip).

## Status colors

Used for booking status chips, room status chips, and calendar bars. Every status shares this palette —
`AVAILABLE`/`CONFIRMED` and `CANCELLED`/`MAINTENANCE` are intentionally the same colors (both mean "not
blocking, no action needed" and "problem state" respectively).

| Status | Background | Text |
|---|---|---|
| `PENDING` | `#F3DFB9` | `#5A3806` |
| `CONFIRMED` | `#CFE5DF` | `#0B3F3A` |
| `CHECKED_IN` | `#D5DCF0` | `#1E2F5C` |
| `CHECKED_OUT` | `#E4E5E1` | `#3E4743` |
| `CANCELLED` | `#F6D6D3` | `#7A1F16` |
| `AVAILABLE` (room) | `#CFE5DF` | `#0B3F3A` |
| `MAINTENANCE` (room) | `#F6D6D3` | `#7A1F16` |

Alert banners (`.alert-success` / `.alert-error` / `.alert-info`) reuse the `CONFIRMED` / `CANCELLED` /
`PENDING` color pairs above rather than defining their own.

## Layouts

Two distinct shells, matching the original UI brief's split between guest and staff experiences.

**Guest layout** (`layout/guest.html`) — top bar (`.topbar`) with the brand, a "Staff area"/"Admin settings"
link for staff/admin roles, an "Account" dropdown (native `<details>`, zero JavaScript) with My bookings /
Become staff / Log out, content centered in a 720px `.wrap`.

**Staff/admin layout** (`layout/staff.html`) — 232px dark sidebar (`.sidebar`, `--sidebar` background),
grouped into a "Staff" section (Dashboard, Room calendar, Search bookings) and an "Admin" section (Rooms,
Room types, Check-in approvals, Staff requests) visible only to `ADMIN`, with live badge counts on the two
approval-queue links. Below 720px viewport width the sidebar collapses into a horizontal top bar.

Both layouts share the same flash-message fragment (`fragments/alert.html`) and the same `.wrap`/`.card`
content styling — only the surrounding chrome differs.

## Component inventory

| Component | Classes | Where used |
|---|---|---|
| Card | `.card` | Forms, list items, detail panels — the base content container everywhere |
| Data table | `.table-wrap`, `.data-table`, `.cell-strong/-muted/-price/-actions` | Admin Rooms, Room types |
| Stat card | `.stat-grid`, `.stat-card`, `.stat-value`, `.stat-label` | Staff dashboard KPIs |
| Chip (status pill) | `.chip`, `.status-*` | Booking/room status everywhere it's shown |
| Badge (count) | `.badge` | Sidebar nav counts, top-nav Staff area count |
| Button | `.btn-primary`, `.btn-danger`, `.btn-sm` modifier | Every form submit / destructive action |
| Form field | `.field`, `label`, `input`, `select`, `.field-error` | Every form — no shared Thymeleaf fragment yet, each template inlines this markup (see gap below) |
| Alert / flash | `.alert`, `.alert-success/-error/-info` | Post-redirect feedback on every page |
| Account menu | `.account-menu`, `.account-menu-panel` (native `<details>`) | Guest top nav |
| Collapsible add-form | `.add-panel` (native `<details>`) | Admin Rooms/Room types "+ Add" forms |
| Occupancy strip | `.occupancy-strip`, `.occupancy-item` | Staff calendar page, per room type |
| Room calendar grid | `.calendar`, `.cal-*` | Staff calendar — CSS grid with absolutely-positioned booking bars |
| Sidebar nav | `.sidebar-nav`, `.sidebar-section` | Staff/admin layout |

## Responsive breakpoints

- **480px** — form rows stack vertically, list items go from horizontal to stacked.
- **720px** — the sidebar layout collapses from a 232px side column to a horizontal top bar; `main.wide`
  (calendar page) drops its max-width at any size once inside `.wrap:has(.wide)`.

No breakpoint below 480px — everything down to ~320px relies on flex-wrap and CSS Grid `auto-fit`
(`.stat-grid`) rather than a dedicated small-phone breakpoint, and that's held up under review: `.wrap`'s
16px side padding leaves enough room for every form/card at 320px, and the stat grid naturally collapses to
one column via `minmax(160px, 1fr)` without any media query needed.

Audited for narrow-viewport overflow and hardened three spots that had no wrap fallback: `.topbar`/`.topnav`,
`.weeknav` (calendar week navigation), and `.page-header` (admin Rooms/Room types) all got `flex-wrap: wrap`
— cheap insurance against clipping if nav text or badges ever get longer than today's content. Also bumped
`.btn-sm` (table row actions) from 32px to 40px min-height, closer to the 44px touch-target guideline the
rest of the app already follows; kept below 44px on purpose so table rows stay visually dense.

Two things that intentionally do **not** reflow, by design, not oversight: the room calendar
(`.calendar`, `min-width: 760px`) and admin data tables (`.data-table`) both scroll horizontally within
their own container (`.calendar-scroll` / `.table-wrap`) rather than collapsing into a stacked mobile
layout. Standard pattern for data-dense grids — the alternative (a card-per-row mobile view) is a real
redesign, not a compatibility fix, and hasn't been asked for.

## Gaps versus the original UI brief (`docs/app-flow-ui-backend.md`)

- Three planned reusable fragments were never built: `fragments/form.html :: field(...)`,
  `fragments/status.html :: chip(...)`, `fragments/pager.html :: pager(...)`. Every template currently
  inlines its own field/chip markup instead — fine at this size, would be worth extracting if the page count
  keeps growing.
- The brief's staff sidebar named "Guests" and "Rates" as nav items. Neither exists — there's no guest
  directory page and no seasonal-rate management UI (rates can currently only be inserted via SQL/migration).
