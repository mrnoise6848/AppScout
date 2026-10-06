# ADR-001: Lightweight Clean Architecture in a single module

**Status:** Accepted · 2026-10

## Context

AppScout mixes several concerns: package discovery, GitHub I/O, version comparison, caching,
background refresh and optional AI. The spec requires Clean Architecture and forbids
"unnecessary Gradle multi-module complexity" for MVP. We must also keep external services
(GitHub, Gemini) replaceable and test every layer without an emulator.

## Decision

* One Gradle module (`app`), Clean Architecture expressed as **packages** with a strict dependency
  direction: `feature → domain ← data`.
* `domain/` is pure Kotlin: models, repository **interfaces**, use cases, version logic. No
  Android, Room, Retrofit or Compose imports.
* `data/` implements the domain interfaces (Room DAOs, Retrofit API, `PackageManager`, DataStore).
* Hilt modules live in one top-level `di/` package rather than beside each layer.
* Cross-cutting infrastructure sits in `core/` (network, database, notification, security, ui).

## Alternatives

1. Multi-module (`:core`, `:domain`, `:data`, `:feature`) with enforced Gradle boundaries.
2. Flat single package with no layering (typical small-app style).
3. "Partial" Clean Architecture: repository interfaces only, use cases in ViewModels.

## Trade-offs

* Package-level boundaries rely on review discipline instead of compiler enforcement.
* A single module means every phase recompiles the whole app (acceptable at MVP size).
* Centralized `di/` is a single point of merge conflicts but makes the graph auditable in one file.

## Consequences

* Domain logic (version comparison, status resolution) is unit-testable on the JVM with no
  Android dependencies.
* Swapping GitHub for another source, or Gemini for another AI, touches only `data/`.
* Promoting a package to a module later is a mechanical move, not a rewrite.
