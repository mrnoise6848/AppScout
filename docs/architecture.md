# AppScout — Architecture

AppScout is a **local-first Android utility**: it reads what is installed on the device, asks
GitHub what the newest published release is, compares versions, and tells the user whether
updating is worth it — including *what changed* — before they tap update.

There is no backend of our own. GitHub is the only remote dependency, and everything the UI needs
is cached on disk so the app stays useful offline.

---

## 1. Layers and dependency direction

Single Gradle module (`app`), Clean Architecture expressed through **packages**, not modules:

```text
Presentation   feature/…, core/ui/…, MainActivity
      │  depends on
   Domain       domain/model, domain/repository (interfaces), domain/usecase, domain/version
      │  implemented by
    Data        data/local, data/remote, data/repository, core/database, core/network
```

Rules:

* `domain/` has **no Android imports** (no `Context`, no Room, no Retrofit, no Compose). It holds
  pure Kotlin models, repository interfaces, and use cases.
* `domain/repository/*.kt` are interfaces; `data/repository/*Impl.kt` implement them. Dependency
  inversion is what keeps the network and database replaceable.
* `feature/*` never talks to repositories directly for writes — it goes through use cases.
* `core/` holds cross-cutting infrastructure: time, dispatchers, database, network, notifications,
  security, shared UI components and navigation. `core/*` may be used from any layer except
  `domain/` (except `core`-free pure types are deliberately not placed there).

> Deviation from the spec's recommended layout: Hilt modules live in a top-level `di/` package
> instead of next to each layer. One place to audit the wiring beats five scattered `@Module`
> files. Documented in [decisions/001-architecture.md](decisions/001-architecture.md).

### Package map

```text
com.noise.appscout/
  AppScoutApplication.kt      Hilt app + WorkManager Configuration.Provider
  MainActivity.kt             single activity, hosts the NavHost

  core/
    common/                   @IoDispatcher/@DefaultDispatcher, TimeProvider
    database/                 Room entities, DAOs, relations, AppScoutDatabase
    network/                  OkHttp clients, retry/backoff, rate-limit mapping
    notification/             notification channels
    security/                 Android Keystore vault for the Gemini API key
    ui/                       shared components, navigation routes, NavHost

  data/
    local/                    PackageManager discovery, DataStore settings
    remote/github/            Retrofit API, release data source, URL parser
    repository/               repository implementations
    mapper/                   entity ↔ domain mappers

  domain/
    model/                    TrackedApp, AppSource, Release, ReleaseStatus, AiSummary, …
    repository/               repository interfaces + result types
    usecase/                  Track / StopTracking / ObserveStatuses / Refresh
    version/                  VersionParser, VersionComparator
    release/                  ReleaseStatusResolver (pure status mapping)

  di/                         Hilt modules (dispatcher, network, database, bindings)
  feature/                    screens + view models (home, apps, releases, settings, ai)
  worker/                     WorkManager refresh worker
```

---

## 2. State management — ViewModel + StateFlow + UDF

* Every screen has one `@HiltViewModel` exposing a single `StateFlow<UiState>`.
* The composable tree is **stateless**: screens take `state` + callbacks, a thin `…Route`
  wrapper owns the ViewModel via `hiltViewModel()` and `collectAsStateWithLifecycle()`.
* All state changes flow through intent functions (`refresh()`, `setNotificationsEnabled(…)`);
  the ViewModel never exposes mutation of the state object itself (unidirectional data flow).
* Derived state (sections, update counts) is computed in the ViewModel so composables only render.

Why this shape: previews and instrumentation tests can drive any screen with plain data, with no
Hilt test runner, no Room and no network.

---

## 3. Persistence — Room as the single source of truth

| Table | Purpose |
| --- | --- |
| `tracked_apps` | user's chosen apps, installed version snapshot, last notified tag |
| `sources` | GitHub source + `etag`, `last_check_at`, `last_error` (per-source health) |
| `releases` | cached releases (tag, notes, assets, published time) |
| `ai_summaries` | structured, validated AI output keyed by release id |

* The UI **only** reads through Room flows (`observeSnapshots()`), so cached data renders
  instantly and offline. Network results are written, then the flow re-emits.
* Conditionals requests use `If-None-Match`/`304` stored per source; clearing the cache also
  drops the ETags (otherwise GitHub would answer `304` for data we no longer have).
* No binary data in the database: icons are always resolved from `PackageManager` at render time.

---

## 4. Networking — GitHub REST only

* Retrofit + OkHttp + `kotlinx.serialization`. One client with a bounded-timeout configuration,
  a deterministic retry helper (only idempotent GETs, exponential backoff), and rate-limit
  detection via `X-RateLimit-*` / `Retry-After`.
* Strategy: `GET /repos/{owner}/{repo}/releases/latest` first (cheapest, spec-mandated). On `404`
  or a payload that is a draft/prerelease, fall back to `GET …/releases?per_page=30` so the app
  can distinguish **not found** from **no releases**, and filter prereleases itself.
* Failures are mapped to a small `SourceErrorReason` enum — never raw exceptions or HTTP codes —
  so the UI shows human explanations and the domain stays transport-agnostic.
* No HTML scraping: releases are read from the documented JSON API only.

---

## 5. Background work — WorkManager

* One **unique periodic** worker (`ReleaseRefreshWorker`), network-constrained, ≥ 12 h interval,
  enqueued with `ExistingPeriodicWorkPolicy.KEEP` so repeated scheduling can never duplicate it.
* Hilt provides the worker factory (`HiltWorkerFactory`); the app removes the default WorkManager
  initializer and implements `Configuration.Provider`.
* The worker reuses `RefreshTrackedAppsUseCase` — the same code path as pull-to-refresh — so
  foreground and background behaviour cannot drift.
* Notifications are only emitted for a status transition, guarded by `last_notified_tag` written
  in the same transaction as the notification decision → no duplicates.

---

## 6. Dependency injection — Hilt

Hilt is justified by two concrete needs: `@HiltWorker` construction and `@AndroidEntryPoint`
activity/ViewModel injection in a Compose app. Modules are centralized in `di/`:

* `DispatcherModule` — qualified dispatchers
* `NetworkModule` — JSON, OkHttp, Retrofit, API
* `DatabaseModule` — Room database, DAOs, `TimeProvider`
* `RepositoryModule` — interface → implementation bindings

---

## 7. AI integration boundary

* `AiSummaryProvider` is a domain-facing interface with two implementations:
  `GeminiAiSummaryProvider` (real, HTTP, BYOK) and a `FakeAiSummaryProvider` used **only in
  tests**.
* The API key lives in an Android Keystore AES-GCM vault (`core/security`), never in DataStore,
  never in the database, never in the repo.
* Provider output is parsed and validated against a fixed contract (`summary`, `why[]`,
  `importance`); anything invalid is dropped and the screen falls back to the raw release notes.
* AI failure never blocks core behaviour: it degrades to "AI summary unavailable". The UI labels
  AI output as advisory.

---

## 8. Version logic

`VersionParser` normalizes tags (`v1.2.3`, `release-1.2.3`, `1.2.3-beta.1`, …) into a numeric
core plus a semver-style prerelease part. `VersionComparator` returns `LOWER/EQUAL/GREATER/
UNCERTAIN`. **`UNCERTAIN` is a first-class result**: when versions cannot be compared the app
reports `VERSION_COMPARISON_UNCERTAIN` instead of guessing, so a false "update available" can
never be produced. `ReleaseStatusResolver` maps cached data + comparator output to the five UI
statuses as a pure function.

---

## 9. Main trade-offs

| Decision | Cost we accept | Why |
| --- | --- | --- |
| Single module | weaker compile-time isolation | MVP size doesn't justify multi-module overhead |
| Room as single source of truth | write indirection | offline correctness + one rendering path |
| Custom version parser | code we own | `semver` libs reject the messy real-world tags apps ship |
| Hilt | annotation/KSP build cost | worker + ViewModel injection with least glue |
| UI reads `PackageManager` icons | repeated lookups | no binary blobs in the DB; always correct icons |
| AI behind an interface | one extra abstraction | it must be optional and testable |

## 10. Rejected alternatives

* **Multi-module Clean Architecture** — premature for a single-app MVP.
* **Flow-heavy "MVI library" / event buses** — plain `StateFlow<UiState>` is enough and debuggable.
* **`QUERY_ALL_PACKAGES`** — forbidden by spec; a `MAIN/LAUNCHER` intent query is sufficient.
* **WebView/HTML scraping of release pages** — brittle and against spec; use the REST API.
* **`security-crypto` EncryptedSharedPreferences** — deprecated; a small Keystore AES-GCM vault
  is explicit and dependency-free.
* **LiveData** — `StateFlow` is Kotlin-native, testable without instrumentation, and composes.
