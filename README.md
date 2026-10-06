# AppScout

> **Know what changed before you update.**

## The Problem

People install Android apps from multiple sources — F-Droid, GitHub, a friend's build, a link in a
forum thread — and then keep using whatever version they happen to have. When a new release shows
up, nobody knows:

* is it actually newer than what I have?
* is it just bug fixes, or something I should care about?
* is it a security fix I should not postpone?
* should I even bother opening the release page?

Release notes are written for people who already follow the project. For everyone else, the answer
is "no idea".

## The Solution

AppScout watches the Android apps you already have installed, checks the GitHub repository you
attach to each one, and tells you — plainly — whether an update is waiting and what it contains.

* Pick an installed app from a list (no `QUERY_ALL_PACKAGES`, no scanning everything).
* Attach its GitHub repository once.
* AppScout keeps track of installed vs. published versions and caches every release locally.
* Optional AI summaries turn raw release notes into a short, clearly-labelled, advisory summary —
  only if you supply your own Gemini API key.

It does **not** download or install anything for you. It does not scrape websites. It does not
phone home to a server of ours: there is no backend at all. When you are offline, everything you
already saw stays readable.

## Demo

End-to-end flow the MVP supports:

```text
1. Launch AppScout
2. Select an installed app
3. Attach a GitHub repository
4. Fetch the latest release
5. Compare installed vs. latest version
6. See UPDATE_AVAILABLE on the dashboard
7. Open release details
8. Generate an AI summary (optional, BYOK)
9. See importance and reasons, labelled AI-generated
10. Open the official GitHub release in the browser
11. Disable the network
12. Reopen AppScout
13. Cached release information is still there (marked as last-known state)
```

> Screenshots and a demo GIF are not included in this repository yet; see
> [Known limitations](#roadmap).

## Features

| Feature | Details |
| --- | --- |
| Installed-app picker | Launcher apps only, with name, package, version and icon |
| GitHub sources | URL parser + `GET /repos/{owner}/{repo}/releases` with ETag revalidation |
| Version intelligence | Prefix-tolerant normalization, semver-style ordering, prerelease filtering |
| Honest uncertainty | Uncomparable versions report `VERSION_COMPARISON_UNCERTAIN`, never a fake update |
| Offline-first | Room is the single source of truth; failed refreshes keep the cache and are flagged stale |
| Background refresh | WorkManager, unique periodic work (12 h), network constraint, `KEEP` policy |
| Notifications | Posted once per release tag, honour the notification setting, tap opens the app |
| AI summaries | Optional, bring-your-own-key Gemini, structured JSON, validated output, advisory only |
| Accessibility | Content descriptions, labelled status pills (never colour alone), 48 dp targets |

## Architecture

Lightweight Clean Architecture with a strict dependency direction:

```text
Presentation (Compose, ViewModel, StateFlow/UDF)
        ↓ depends on
Domain (models, use cases, repository interfaces, version logic)
        ↓ implemented by
Data (Room, Retrofit/GitHub+Gemini, PackageManager, DataStore)  +  core/ (Hilt modules, notifications)
```

* Screens are **stateless composables** fed by a thin `…Route` wrapper → previews and UI tests need
  no DI.
* `ViewModel` exposes `StateFlow<UiState>`; every user intent is a function, every state is data.
* Room is the only source of truth; network results are written first, then observed.
* Background work calls the *same* use cases as the foreground, so behaviour cannot drift.

Full write-up: [`docs/architecture.md`](docs/architecture.md) · Decision records:
[`docs/decisions/`](docs/decisions).

## Technology

| Area | Choice |
| --- | --- |
| UI | Jetpack Compose + Material 3 (Compose BOM `2026.09.00`) |
| Language / build | Kotlin 2.4.0, AGP 9.4.1, Gradle 9.8.0, Java 11 bytecode |
| DI | Hilt |
| Persistence | Room (exported schemas) + DataStore for settings |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx.serialization; GitHub REST only |
| Background | WorkManager 2.12 |
| Navigation | Navigation Compose (type-safe routes) |
| AI | Google Gemini `generateContent` (structured JSON), optional |
| CI | GitHub Actions → `lint`, `test`, `assembleDebug` |

## Privacy

* No backend, no analytics, no third-party SDKs.
* Only two network destinations exist: `api.github.com` and, if you enable AI and provide a key,
  `generativelanguage.googleapis.com`.
* Your Gemini API key is stored **encrypted with the Android Keystore** on the device, never in the
  repository, never in logs. Remove it at any time from Settings.
* AppScout reads the list of launcher apps and their versions. It does **not** request
  `QUERY_ALL_PACKAGES`, never reads app contents, and never installs packages.

## Testing

```bash
./gradlew lint test assembleDebug
```

86 JVM unit tests cover every layer:

* `GitHubSourceUrlParserTest` — accepted/rejected source URLs
* `GitHubReleaseDataSourceTest` — ETag/304, 404, rate limit, retry policy, malformed payloads
* `VersionParserTest` / `VersionComparatorTest` / `ReleaseStatusResolverTest` — normalization,
  ordering, prereleases, uncertain comparisons, stale-but-cached offline decisions
* `EntityMappersTest` — release/source cache round-trips
* `TrackAppUseCaseTest` — attach a source, nothing persisted on failure
* `RefreshTrackedAppsUseCaseTest` — background pipeline: success keeps cache, offline keeps cache
* `NotifyNewReleasesUseCaseTest` — notify once per tag, settings honoured
* `RefreshSchedulerTest` — unique work name + `KEEP` policy ⇒ no duplicate periodic work
* `GeminiAiSummaryProviderTest` — HTTP boundary: valid/malformed output, key errors, rate limits,
  evidence constraint (unsupported security claims are dropped)
* `GenerateAiSummaryUseCaseTest` — cached vs. generated, AI disabled, graceful failure

Instrumented tests (`./gradlew connectedDebugAndroidTest`) include an end-to-end acceptance test
that attaches a real public repository to a real installed app; they run when a device or emulator
is available (also wired into CI when one is).

## Roadmap

* Screenshots / demo GIF
* Instrumented UI tests in CI once an emulator runner is available
* Per-app refresh interval and notification channel preferences
* Play Store release build (signing is deliberately out of this repo)

## License

MIT — see [`LICENSE`](LICENSE). Third-party dependencies are Apache-2.0 licensed (except as noted)
and listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
