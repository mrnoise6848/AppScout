# AppScout — Product Specification & Agent Instructions

## 1. Product Definition

AppScout is an Android utility that helps users understand whether installed apps have meaningful new releases and whether those updates are worth installing.

### Core value proposition

> **Don't just tell me that an app has an update. Tell me what changed and whether I should care.**

AppScout is **not** a generic app updater.

The MVP is GitHub-first, local-first, Android-native, and does not require a backend.

### Core flow

```text
Installed App
    ↓
Tracked GitHub Repository
    ↓
Latest Published Release
    ↓
Compare Installed vs Latest Version
    ↓
Update Status
    ↓
Release Notes
    ↓
Optional AI Summary
    ↓
User decides whether to update
```

AI is advisory. The app must never present AI output as authoritative security or safety certification.

---

# 2. Primary User Problem

Users install apps from different sources and often cannot answer:

* Is this app updated?
* What changed?
* Is this update important?
* Does it mention security fixes?
* Is it mainly bug fixes?
* Should I update now or can I wait?

AppScout reduces this decision-making friction.

---

# 3. MVP Scope

## Must Have

### A. Installed App Discovery

Show user-facing installed Android applications that can be launched.

Use Android package/intent APIs.

Do **not** request `QUERY_ALL_PACKAGES`.

Use restricted package visibility for launcher-visible applications.

Each app item should expose:

* app name
* package name
* version name
* version code when available
* application icon

The user can select an installed app to track.

System/internal packages that are not user-launchable do not need to appear in MVP.

---

### B. GitHub Source Mapping

Allow the user to attach a GitHub repository to a tracked app.

Example:

```text
Firefox
Package:
org.mozilla.firefox

GitHub:
https://github.com/mozilla-mobile/firefox-android
```

Supported GitHub URL forms:

```text
https://github.com/owner/repository
https://github.com/owner/repository/
https://github.com/owner/repository.git
```

Invalid or unsupported URLs must fail gracefully.

GitHub is the only source provider in v1.

Do not implement arbitrary source discovery in MVP.

---

### C. Release Checking

For each tracked app:

1. Read currently installed version.
2. Fetch the latest published GitHub release.
3. Ignore draft releases.
4. Ignore prereleases by default.
5. Compare installed version with release version/tag.
6. Return one of:

```text
UP_TO_DATE
UPDATE_AVAILABLE
VERSION_COMPARISON_UNCERTAIN
SOURCE_ERROR
NOT_INSTALLED
```

The UI must clearly distinguish these states.

Use GitHub's official Releases REST API.

Do not scrape GitHub HTML.

---

### D. Version Normalization

Implement a dedicated version comparison component.

Examples:

```text
1.2.3
v1.2.3
release-1.2.3
```

should be normalized where safely possible.

Do not blindly assume every GitHub tag follows Semantic Versioning.

For unsupported or ambiguous versions, return:

```text
VERSION_COMPARISON_UNCERTAIN
```

Never claim an update exists when the comparator cannot prove it.

---

### E. Release Details

Show:

* installed version
* latest release version/tag
* release title
* published date
* release URL
* release notes
* release assets when available
* update status

Do not download APKs automatically.

The "Update" action opens the official GitHub release page in the browser.

Automatic installation is explicitly out of scope.

---

### F. AI Changelog Summary

AI is an enhancement, not the source of truth.

Create:

```kotlin
interface AiSummaryProvider {
    suspend fun summarizeRelease(
        appName: String,
        installedVersion: String,
        latestVersion: String,
        releaseNotes: String
    ): AiSummary
}
```

Production implementation:

```text
GeminiSummaryProvider
```

Testing implementation:

```text
FakeAiSummaryProvider
```

AI must be optional.

If no API key is configured, the rest of the application must work normally.

The app must never become unusable because AI is disabled.

---

# 4. AI Output Contract

AI must return structured JSON.

Example:

```json
{
  "summary": "Short human-readable summary",
  "importance": "LOW",
  "reasons": [
    "Bug fixes",
    "Minor UI improvements"
  ],
  "security_related": false,
  "breaking_change_possible": false
}
```

Allowed importance:

```text
LOW
MEDIUM
HIGH
UNKNOWN
```

Rules:

* AI may only use supplied release notes as evidence.
* AI must not invent changes.
* AI must not claim security fixes unless release notes explicitly support them.
* If evidence is insufficient, return `UNKNOWN`.
* AI output must be labeled as AI-generated.
* Original release notes must always remain available.

---

# 5. User Decision Model

AppScout should not pretend to know the objectively correct decision.

Example:

```text
Update available

v1.4.1 → v1.5.0

Importance: HIGH

Why:
• Security fixes mentioned in release notes
• Several bug fixes
• New major feature

Recommendation:
Consider updating soon.
```

Low-impact example:

```text
Importance: LOW

Mostly maintenance and minor fixes.

Recommendation:
You can probably wait if the current version works well for you.
```

Never use claims such as:

```text
This update is definitely safe.
You must install this.
This update contains no risk.
```

---

# 6. Architecture Decision Rule

## Architecture First

Before implementation, inspect the requirements and choose the simplest production-grade architecture that provides:

* separation of concerns
* testability
* maintainability
* replaceable infrastructure
* clear dependency direction
* reasonable scalability

Prefer **Clean Architecture principles with Presentation / Domain / Data separation**, but keep them lightweight.

Do not introduce unnecessary layers, interfaces, modules, frameworks, or abstractions merely to "look enterprise".

Architecture must be justified by project needs.

If a different architecture is demonstrably better for a specific part of the system, propose it before implementation.

Do not silently change architecture.

### Recommended architecture for MVP

```text
Presentation
    ↓
Domain
    ↓
Data
```

Dependency direction:

```text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository Interface
 ↓
Repository Implementation
 ↓
Room / GitHub / Gemini
```

The Domain layer must not depend on Android framework APIs, Retrofit, Room, Gemini SDKs, or other infrastructure details.

### State management

Use:

```text
ViewModel
+
StateFlow
+
Unidirectional Data Flow
```

Conceptually:

```text
User Action
    ↓
ViewModel
    ↓
Use Case
    ↓
Repository
    ↓
Result
    ↓
StateFlow
    ↓
Compose UI
```

Business logic must not live inside Composables.

### SOLID

Apply SOLID principles pragmatically.

Avoid abstraction for abstraction's sake.

### Dependency Injection

Use Hilt if it reduces complexity and improves testability.

Do not introduce excessive DI layers or dozens of interfaces without real value.

### Replaceable external services

External providers must remain replaceable.

Example:

```text
Domain
  ↓
AiSummaryProvider
  ↑
GeminiSummaryProvider
FakeAiSummaryProvider
```

GitHub and Gemini are infrastructure, not domain knowledge.

---

# 7. Architecture Decision Records

Create:

```text
docs/
  architecture.md
  decisions/
    001-architecture.md
    002-state-management.md
    003-local-cache.md
    004-background-refresh.md
    005-ai-provider.md
    006-no-automatic-apk-installation.md
```

Each ADR should include:

```text
Context
Decision
Alternatives
Trade-offs
Consequences
```

The agent must document material architecture decisions.

---

# 8. Local-First Architecture

No backend is required for MVP.

Conceptual architecture:

```text
                ┌─────────────────────┐
                │    Jetpack Compose  │
                │         UI          │
                └──────────┬──────────┘
                           │
                    ViewModel / State
                           │
                ┌──────────▼──────────┐
                │      Use Cases      │
                └──────────┬──────────┘
                           │
                ┌──────────▼──────────┐
                │     Repositories    │
                └──────┬───────┬──────┘
                       │       │
             ┌─────────▼─┐ ┌──▼───────────┐
             │ Room DB   │ │ GitHub API   │
             └───────────┘ └──────────────┘
                       │
                ┌──────▼──────┐
                │ WorkManager │
                └─────────────┘

Optional:
                ┌─────────────────────┐
                │   Gemini Provider   │
                └─────────────────────┘
```

---

# 9. Project Structure

Do not create unnecessary Gradle multi-module complexity for MVP.

Recommended package structure:

```text
app/
  src/main/java/com/appscout/
    AppScoutApplication.kt

    core/
      common/
      model/
      network/
      database/
      security/
      ui/

    data/
      local/
      remote/
      repository/

    domain/
      model/
      repository/
      usecase/

    feature/
      apps/
      tracking/
      releases/
      settings/
      ai/

    worker/

    MainActivity.kt
```

Feature packages may contain:

```text
screen
viewmodel
state
ui-model
```

Keep domain logic out of presentation.

---

# 10. Domain Models

At minimum:

```text
TrackedApp
AppSource
Release
ReleaseStatus
AiSummary
UpdateImportance
```

Conceptual model:

```kotlin
TrackedApp(
    id,
    packageName,
    appName,
    currentVersionName,
    currentVersionCode,
    iconReference,
    sourceId,
    enabled,
    createdAt,
    updatedAt
)
```

```kotlin
AppSource(
    id,
    type,
    owner,
    repository,
    url
)
```

```kotlin
Release(
    id,
    sourceId,
    tagName,
    normalizedVersion,
    title,
    body,
    htmlUrl,
    publishedAt,
    isPrerelease,
    isDraft,
    fetchedAt
)
```

Do not store binary application icons in Room.

Use Android PackageManager information for installed app metadata.

---

# 11. Database

Use Room.

Required entities:

```text
TrackedAppEntity
SourceEntity
ReleaseEntity
AiSummaryEntity
```

Use foreign keys where appropriate.

Store release notes locally so the user can inspect the last successful result offline.

The UI must remain useful without network access.

---

# 12. Networking

Use:

```text
Retrofit
OkHttp
kotlinx.serialization OR Moshi
```

Choose one serialization approach and use it consistently.

GitHub API client must support:

```text
GET /repos/{owner}/{repo}/releases/latest
```

Do not scrape HTML.

Support:

* connection timeout
* read timeout
* safe retries for transient failures
* exponential backoff
* ETag / conditional requests where practical
* rate-limit handling
* last-success cache

Never aggressively poll GitHub.

---

# 13. Background Refresh

Use WorkManager.

Requirements:

* periodic refresh
* network constraint
* unique work
* no duplicate scheduling
* no unnecessary wakeups

Suggested interval:

```text
12 hours or longer
```

Android controls exact background execution time.

Worker responsibilities:

```text
load tracked apps
    ↓
refresh releases
    ↓
update local database
    ↓
detect newly available releases
    ↓
optionally notify user
```

Worker must not manipulate Compose UI directly.

---

# 14. Notifications

When an update becomes newly available, show a concise notification.

Example:

```text
Firefox update available
v143 → v144
Security fixes mentioned
```

Do not repeatedly notify for the same release.

Persist notification state.

Settings:

```text
Update notifications:
ON / OFF
```

---

# 15. Screens

## Home

Sections:

```text
Updates Available
Up to Date
Needs Attention
```

Example:

```text
AppScout

2 updates available

Firefox       v143 → v144
Obtainium     v1.2 → v1.3

Up to date
VLC
Signal
```

Empty state:

```text
No tracked apps yet.

Add an app to start monitoring releases.
```

---

## Add App

Flow:

```text
Select installed app
      ↓
Enter GitHub repository
      ↓
Validate repository
      ↓
Fetch latest release
      ↓
Confirm tracking
```

Validation errors should be immediate and understandable.

---

## App Details

Show:

```text
App icon
App name
Installed version

Source
github.com/owner/repo

Latest release
v1.5.0

Status
UPDATE AVAILABLE
```

Actions:

```text
Check now
Open release
Summarize with AI
Stop tracking
```

---

## Release Details

Show:

```text
v1.5.0

Published:
October 2, 2026

Importance:
MEDIUM

AI Summary:
...

Why:
• Bug fixes
• Performance improvements

Original release notes
----------------------
...
```

Action:

```text
Open GitHub Release
```

---

## Settings

Include:

```text
Notifications
Background refresh
AI settings
Gemini API key
Clear cached release data
About
Open Source licenses
```

---

# 16. UI/UX Rules

Use:

* Jetpack Compose
* Material 3
* light/dark theme
* accessible components
* clear hierarchy
* useful empty states
* clear update state
* responsive layouts

Avoid:

* excessive animations
* gradients everywhere
* unnecessary dashboards
* fake statistics
* decorative "AI" branding
* generic chatbot UI
* visual complexity that hides the primary task

This is a utility app.

The primary value should be understood within seconds.

---

# 17. Explicit Non-Goals for MVP

Do NOT implement:

* automatic APK installation
* APK downloading
* F-Droid integration
* GitLab integration
* Google Play integration
* arbitrary website scraping
* cloud account
* user registration
* subscription
* analytics
* social features
* multi-device sync
* custom ML models
* vector database
* RAG
* conversational chatbot

Do not expand the MVP because an extra feature seems interesting.

---

# 18. Security

The repository must contain no:

```text
API keys
tokens
passwords
private credentials
```

No Gemini key may be hardcoded.

For public usage, support Bring Your Own Key.

Store user credentials using secure Android storage backed by Android Keystore where appropriate.

Never log:

* API keys
* auth headers
* credentials

Use HTTPS.

Before completion, inspect both current files and Git history for accidental secrets.

---

# 19. Performance

Requirements:

* no network calls on the main thread
* no blocking database operations on the main thread
* no blocking PackageManager scanning on the UI thread
* async release parsing
* large release notes must not freeze Compose
* background work remains outside the UI layer

Home screen must remain responsive while refresh runs.

---

# 20. Testing Strategy

Testing is part of implementation.

Required layers:

```text
Domain / Unit
Data / API
Repository
Worker
Compose UI
End-to-End demo
```

Do not consider a feature complete until implementation, tests, and verification are complete.

---

# 21. Acceptance Tests

## A. URL Parsing

Input:

```text
https://github.com/owner/repo
```

Expected:

```text
owner = owner
repository = repo
```

Also test:

```text
https://github.com/owner/repo/
https://github.com/owner/repo.git
```

Invalid URLs must fail gracefully.

---

## B. Version Parsing

These should normalize equivalently where supported:

```text
1.2.3
v1.2.3
```

Expected:

```text
1.2.3
```

---

## C. Version Comparison

Verify:

```text
1.2.0 < 1.3.0
1.3.0 > 1.2.0
1.2.3 = 1.2.3
```

---

## D. Version Comparison Uncertainty

Input:

```text
installed = 2026.09
release = stable-latest
```

Expected:

```text
VERSION_COMPARISON_UNCERTAIN
```

Never report UPDATE_AVAILABLE in this case.

---

## E. Prerelease Filtering

Given:

```text
v2.0.0-beta.1
v1.9.0
```

with prereleases disabled, expected latest:

```text
v1.9.0
```

---

## F. Update Detection

Installed:

```text
1.9.0
```

Latest:

```text
v2.0.0
```

Expected:

```text
UPDATE_AVAILABLE
```

---

## G. Up to Date

Installed:

```text
2.0.0
```

Latest:

```text
v2.0.0
```

Expected:

```text
UP_TO_DATE
```

---

## H. GitHub 404

Repository does not exist.

Expected:

```text
SOURCE_ERROR
```

No crash.

---

## I. GitHub Rate Limit

Simulate HTTP 403/rate-limit.

Expected:

* cached data remains visible
* helpful error state
* no crash
* no infinite retries

---

## J. Offline Mode

When offline:

* tracked apps remain visible
* last known release remains visible
* stale/offline status is visible
* no crash

---

# 22. API Tests

Use MockWebServer or an equivalent test server.

Test:

```text
200 latest release
200 changed release
304 not modified
404 repository
403 rate limit
500 server error
malformed JSON
empty release list
release with no body
release with prerelease
```

All cases must produce deterministic behavior.

---

# 23. AI Tests

Use FakeAiSummaryProvider.

## Valid response

Input release notes:

```text
Fixed crash when opening settings.
Improved startup performance.
```

Expected:

```text
importance = LOW or MEDIUM
security_related = false
```

Exact importance may depend on the implemented deterministic validation/AI contract, but security_related must remain false for these notes.

## Malformed response

Return invalid JSON.

Expected:

```text
AI summary unavailable
```

Release details must remain usable.

## No AI key

Expected:

* original release notes remain available
* no crash
* no blocked core functionality

## Evidence constraint

Test that generated AI output is validated and cannot silently introduce unsupported security claims or other facts not grounded in the supplied release notes.

---

# 24. Compose UI Tests

Create UI tests for:

1. empty home state
2. home with one tracked app
3. update available card
4. up-to-date card
5. source error
6. add-app flow
7. invalid GitHub URL
8. app details
9. release details
10. AI loading
11. AI error
12. settings
13. dark theme critical states

---

# 25. WorkManager Tests

Use WorkManager testing APIs.

Verify:

### Successful worker

```text
tracked apps
→ fetch release
→ save release
→ update status
```

### Offline worker

```text
network unavailable
→ retry/failure according to policy
→ cached state preserved
```

### Duplicate scheduling

Scheduling refresh repeatedly must not create duplicate periodic work.

Use a unique work name.

---

# 26. Notification Tests

Verify:

1. new release triggers notification
2. same release does not repeatedly notify
3. notifications can be disabled
4. notification opens the correct destination
5. notification content is concise and safe

---

# 27. Accessibility

At minimum:

* content descriptions for meaningful icons
* accessible touch targets
* important status is not communicated through color alone
* readable contrast
* dynamic text does not break critical screens

---

# 28. CI

Create GitHub Actions.

At minimum:

```text
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

Add instrumentation tests in CI when a suitable emulator is available.

CI must fail on:

* compilation errors
* unit test failures
* relevant lint failures

---

# 29. README Requirements

README must serve both technical and non-technical visitors.

Do NOT start with a technology list.

Start with the user problem.

Recommended opening:

# AppScout

> **Know what changed before you update.**

Then include:

## The Problem

People install apps from multiple sources but usually do not know whether an available release is worth installing.

## The Solution

AppScout tracks selected Android apps, checks their GitHub releases, explains what changed, and optionally summarizes release notes with AI.

Then include:

```text
Demo GIF / screenshots
Features
Architecture
Technology
Privacy
Testing
Roadmap
License
```

The README should include a short demo GIF when practical.

---

# 30. Demo Scenario

The final MVP must support this complete demo:

```text
1. Launch AppScout
2. Select an installed app
3. Attach a GitHub repository
4. Fetch latest release
5. Show current vs latest version
6. Show UPDATE_AVAILABLE
7. Open release details
8. Generate AI summary
9. Show importance and reasons
10. Open official GitHub release
11. Disable network
12. Reopen AppScout
13. Cached release information remains visible
```

This must work without a backend.

---

# 31. Agent Workflow

You are the implementation agent.

Your required workflow:

```text
Inspect
→ Architecture Review
→ Plan
→ Implement
→ Test
→ Review
→ Fix
→ Verify
```

## Step 0 — Inspect before coding

Before modifying files:

1. inspect repository structure
2. inspect Gradle configuration
3. inspect Android manifest
4. inspect package structure
5. inspect existing tests
6. inspect build configuration
7. detect existing architecture
8. identify existing dependencies
9. identify reusable code
10. identify constraints

Do not immediately rewrite the repository.

---

## Step 1 — Architecture Review

Before implementation, create or update:

```text
docs/architecture.md
docs/decisions/
```

Document:

* chosen architecture
* dependency direction
* state-management strategy
* persistence strategy
* networking strategy
* background work strategy
* DI strategy
* AI integration boundary
* main trade-offs
* rejected alternatives

The architecture should be simple enough for the current product but structured enough to evolve.

If the repository's existing architecture is already good, preserve it.

---

## Step 2 — Phase planning

Before each major phase:

1. inspect current state
2. identify dependencies
3. identify acceptance tests
4. implement only that phase
5. run relevant tests
6. report results

Do not silently skip acceptance tests.

---

# 32. Rules for Existing Code

If the repository already contains useful code:

* reuse it
* refactor only when justified
* preserve working behavior
* avoid unnecessary rewrites

If a major architectural change is required:

STOP and report:

```text
Current architecture
Proposed architecture
Reason
Trade-offs
Affected files/modules
```

Do not silently make a major architectural change.

---

# 33. Dependency Rules

Before adding a dependency, ask:

> Can AndroidX, Kotlin, or the existing stack solve this cleanly?

Avoid dependency explosion.

Prefer stable, well-maintained libraries.

Do not add a library for trivial functionality.

Use one solution consistently for each concern.

---

# 34. No Fake Implementations

Do not create production placeholders such as:

```text
TODO
FIXME
fake network response
hardcoded production data
temporary production bypass
```

Mocks/fakes are allowed only in tests.

Do not replace a required feature with a mock simply to make tests pass.

---

# 35. Implementation Phases

## Phase 1 — Foundation

Implement:

* Android project
* Kotlin
* Compose
* Material 3
* dependency setup
* navigation
* Room
* lightweight Clean Architecture
* ViewModel + StateFlow + UDF
* Hilt if justified
* CI
* architecture documentation

Acceptance:

```text
build passes
lint passes
tests pass
app launches
architecture.md exists
```

---

## Phase 2 — Installed App Discovery

Implement:

* package discovery
* launcher app filtering
* installed version retrieval
* selection UI

Acceptance:

User can select a real installed app and see:

```text
name
package
version
icon
```

---

## Phase 3 — GitHub Source

Implement:

* GitHub URL parser
* source model
* repository validation
* GitHub API client
* latest release retrieval

Acceptance:

A real public GitHub repository can be attached to a real installed app.

---

## Phase 4 — Version Tracking

Implement:

* version normalization
* semantic comparison
* uncertain state
* cached release state

Acceptance:

All version tests pass.

---

## Phase 5 — Home + Release UI

Implement:

* home dashboard
* update states
* release details
* browser link
* offline cache

Acceptance:

Complete demo scenario works except AI.

---

## Phase 6 — Background Refresh

Implement:

* WorkManager
* unique periodic work
* network constraints
* notifications
* duplicate-notification prevention

Acceptance:

Worker tests pass.

---

## Phase 7 — Gemini Integration

Implement:

* AiSummaryProvider
* Gemini provider
* secure API key storage
* structured output
* output validation
* fake provider
* graceful failure

Acceptance:

AI remains optional and core functionality works without it.

---

## Phase 8 — Polish

Implement:

* accessibility
* dark/light theme
* loading states
* error handling
* useful animations only
* README
* demo GIF
* screenshots
* license notices
* final cleanup

---

# 36. Final Definition of Done

The project is complete only when:

[ ] Build succeeds.

[ ] Unit tests pass.

[ ] UI tests pass.

[ ] Worker tests pass.

[ ] GitHub API integration works with a real public repository.

[ ] Installed app version is detected correctly.

[ ] Update detection works.

[ ] Ambiguous versions do not produce false update decisions.

[ ] Release notes are cached locally.

[ ] Useful read-only behavior works offline.

[ ] Background refresh is implemented.

[ ] Notifications do not duplicate.

[ ] Gemini is optional.

[ ] No secrets exist in the repository.

[ ] README explains the user problem before the technology.

[ ] Demo flow works end-to-end.

[ ] No MVP non-goal was implemented unnecessarily.

[ ] Architecture is documented.

[ ] Major architecture decisions are documented as ADRs.

[ ] The repository is clean enough for public GitHub.

[ ] The implementation contains no unexplained architectural shortcuts.

---

# 37. Final Agent Reporting Format

At the end of every phase, report exactly:

```text
## Implemented
- ...

## Tests
- ...

## Failures
- None / ...

## Known Limitations
- ...

## Architecture Notes
- ...

## Next Phase
- ...
```

At final completion, additionally report:

```text
## Final Verification
Build:
Tests:
Lint:
UI Tests:
Worker Tests:
APK:
Known Limitations:
```

The goal is not maximum feature count.

The goal is:

```text
Correctness
+
Clarity
+
Reliability
+
Testability
+
Maintainability
+
Excellent UX
```

A smaller polished product is preferred over a larger unfinished one.
