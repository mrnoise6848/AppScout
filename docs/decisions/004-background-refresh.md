# ADR-004: Single unique periodic WorkManager job with deduplicated notifications

**Status:** Accepted · 2026-10

## Context

Users want to know about new releases without opening the app. Android forbids long-running
background services, Doze kills timers, and duplicate work/notification is an explicit acceptance
failure. The spec requires a unique periodic worker, a network constraint, an interval ≥ 12 h and
no duplicate notifications.

## Decision

* One worker, `ReleaseRefreshWorker`, registered as **unique periodic work** with
  `ExistingPeriodicWorkPolicy.KEEP`, `NetworkType.CONNECTED` constraint and a ≥ 12 h interval.
  Enqueue calls are idempotent by construction.
* The worker is a `@HiltWorker` using the app-provided `HiltWorkerFactory`; the default
  WorkManager initializer is removed from the manifest and `Configuration.Provider` is implemented
  by `AppScoutApplication`.
* It reuses `RefreshTrackedAppsUseCase` — identical to pull-to-refresh — then post-processes
  results into notifications.
* Duplicate prevention: `tracked_apps.last_notified_tag` records the release tag the user was
  already told about. A notification is sent only when the tag changes, and the tag is written in
  the same call that makes the decision. Repeat runs with unchanged data are silent.
* Notifications respect `settings.notifications_enabled` and the channel created in
  `core/notification`.
* If `background_refresh_enabled` is off, the worker exits without work instead of being
  cancelled/re-created (state stays simple and re-enabling is instant).

## Alternatives

1. `AlarmManager` + `JobScheduler` directly.
2. Foreground service polling.
3. FCM push from GitHub (requires our own backend — forbidden).
4. Separate workers per tracked app.

## Trade-offs

* Periodic work is best-effort: exact timing is not guaranteed (acceptable for release checks).
* 12 h minimum means a release can be reported late if the device is idle all day.
* One worker loops over all apps — fine for a personal tracking list, needs chunking at scale.

## Consequences

* Re-enqueueing on app start never duplicates work (`KEEP`).
* A single test seam (`RefreshTrackedAppsUseCase`) covers foreground and background behaviour.
* Notification correctness reduces to one invariant: *notify iff tag != last_notified_tag*.
