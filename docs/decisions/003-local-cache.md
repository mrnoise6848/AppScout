# ADR-003: Room as the single source of truth (local-first cache)

**Status:** Accepted · 2026-10

## Context

The product promise is "useful read-only behaviour offline" and instant startup. GitHub is the
only data source, it is rate-limited, and release notes must survive process death. The spec
requires Room caching and forbids a backend of our own.

## Decision

* Every read the UI performs comes from Room via `Flow` (`observeSnapshots()`); the network is
  only ever a *writer*.
* Tables: `tracked_apps`, `sources`, `releases`, `ai_summaries`. `sources` carries the HTTP
  `etag`, `last_check_at` and `last_error` so per-source health and conditional requests are
  persisted with the data they describe.
* Refresh = `fetch → persist → flow re-emits`; foreground refresh and the background worker share
  this exact path.
* Failures are persisted too, so a stale-but-known release renders as "cached + last check failed"
  instead of disappearing.
* Clearing the cache also resets `etag` (otherwise GitHub would answer `304` for data we deleted).
* Icons are **not** stored: the presentation layer resolves them from `PackageManager`.

## Alternatives

1. In-memory cache + re-fetch on every launch (repository pattern without persistence).
2. DataStore/JSON files as the cache.
3. PagingSource/paged release lists.

## Trade-offs

* Migrations must be handled once schema evolves (Room exports schemas to `app/schemas`).
* Writing through Room adds boilerplate entities/mappers versus a JSON blob.
* Repository `Flow` chains can hide where work happens — mitigated by keeping network calls out
  of DAOs and inside `ReleaseRepositoryImpl`.

## Consequences

* Cold start renders tracked apps and their last known release with zero network.
* Offline mode degrades gracefully: cached release, stale flag, human error reason.
* Tests can seed Room (or a fake repository) and assert UI output deterministically.
