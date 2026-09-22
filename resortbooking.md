# Resort Booking System

A booking platform for a small Philippine resort: guests search availability, book a room and manage their own reservations; front desk staff run a weekly room calendar; admins manage room types, rooms and seasonal rates. Built with Spring Boot, Hibernate/JPA and PostgreSQL, server-rendered with Thymeleaf, with a parallel `/api/v1` JSON API secured by JWT.

This file is the project index. Full specs live in [`docs/`](docs/):

- **[PRD & TRD](docs/prd-trd.md)** — product requirements (problem, goals, user roles, functional requirements, business rules) and technical requirements (architecture, tech stack, data model, REST API, security, concurrency, testing strategy, milestones).
- **[App Flow, UI Brief & Backend Schema](docs/app-flow-ui-backend.md)** — guest and staff page flows, request lifecycle, UI design tokens, Thymeleaf page inventory and conventions, Hibernate entities, repository queries, and the Flyway SQL schema.

The two files above are the original spec — they don't change as the build progresses. Two more **living**
documents do, and are meant to be edited as the design evolves:

- **[Design System](docs/design-system.md)** — the current design (colors, typography, components) as
  actually implemented, kept up to date for planning new colorways/components before they're built.
- **[Frontend Modernization](docs/frontend-modernization.md)** — planning notes for the move to React +
  Tailwind, what the existing `/api/v1` backend already supports and what it doesn't yet.

For **what is actually built right now**, how to run it, and the concurrency/security reasoning behind it,
see **[README.md](README.md)**.

## At a glance

- **Core rule:** a room can never have two active bookings with overlapping dates (BR-01), enforced both in the service layer (`SELECT … FOR UPDATE`) and in PostgreSQL (`EXCLUDE USING gist`).
- **Roles:** Guest, Front Desk, Admin — see [Users and roles](docs/prd-trd.md#users-and-roles).
- **Booking lifecycle:** `PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT`, or `CANCELLED` from `PENDING`/`CONFIRMED` (BR-08).
- **Stack:** Java 21, Spring Boot, Spring MVC + Thymeleaf (server-rendered pages), Spring Security (session login for pages, JWT for `/api/v1`), Hibernate/Spring Data JPA, PostgreSQL 16, Flyway, Maven.
- **Money:** `BigDecimal`, 2 decimal places, PHP — never floating point (BR-07).
- **Time zone:** Asia/Manila for all dates (NFR-07).

## Project layout

```
resortbooking/
├── docs/
│   ├── prd-trd.md                 Product & technical requirements
│   └── app-flow-ui-backend.md     App flow, UI brief, backend schema
├── src/
│   ├── main/
│   │   ├── java/com/resortapi/resortbooking/
│   │   │   ├── config/        Spring configuration (security, beans)
│   │   │   ├── controller/    Spring MVC page controllers and REST controllers
│   │   │   ├── dto/           DTOs and form-binding objects
│   │   │   ├── entity/        JPA entities and their enums
│   │   │   ├── exception/     Custom exceptions and the global handler
│   │   │   ├── repository/    Spring Data JPA repositories
│   │   │   └── service/       Business rules, @Transactional boundaries
│   │   └── resources/
│   │       ├── templates/          Thymeleaf pages (layout, fragments, guest, staff, admin, auth)
│   │       ├── static/css/app.css  Shared stylesheet
│   │       └── db/migration/       Flyway migrations (V1__init.sql, V2__seed.sql, ...)
│   └── test/java/com/resortapi/resortbooking/
│       ├── controller/  repository/  service/
├── pom.xml
├── mvnw / mvnw.cmd
└── resortbooking.md                This file
```

Two deliberate deviations from the [TRD](docs/prd-trd.md): the base package is `com.resortapi.resortbooking` (matching the pom's groupId) rather than `com.rai.resortbooking`, and the code is organised **by layer** (`controller/`, `service/`, `repository/`, `entity/`) rather than the package-by-feature layout the TRD describes.

## Milestones

See [Milestones](docs/prd-trd.md#milestones) for the full M1–M8 plan (spec & setup → core entities → bookings & availability → security → testing → rates/XML → microservices → performance & ship).

## Open questions

Tracked in [PRD open questions](docs/prd-trd.md#open-questions). The frontend-stack question (Thymeleaf vs
React) was originally resolved in favor of server-rendered Thymeleaf, per the [App Flow doc](docs/app-flow-ui-backend.md) — that's being revisited now in favor of React + Tailwind; see
[Frontend Modernization](docs/frontend-modernization.md) for what that move actually involves.
