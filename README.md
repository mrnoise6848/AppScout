# AppScout

**Know what changed before you update.**

Apps installed from GitHub can fall outside the usual store-update workflow. Checking them means finding each repository, comparing release tags with installed versions, and reading the notes to decide what needs attention.

AppScout brings that work into an Android dashboard. Link an installed app to its repository once, then review new releases, read what changed and open the official release when you are ready. Cached details remain available offline.

## From installed app to informed update

1. **Connect the source.** Choose a launcher app and attach its GitHub repository.
2. **See where it stands.** Compare the installed version with the published release and inspect the release notes.
3. **Read a shorter explanation if useful.** Optional Gemini summaries use your own API key and appear as advisory, AI-generated content alongside the source notes.
4. **Act on the official release.** Open GitHub to review or obtain the update. Installation remains outside AppScout.

Background checks use WorkManager, with a 12-hour interval subject to Android scheduling. Notifications are deduplicated by release tag so the same release does not repeatedly demand attention.

## Three different answers deserve three different states

| Answer | Meaning |
|---|---|
| Update available | Parsed versions indicate that the published release is newer |
| Version comparison uncertain | A version format cannot be compared confidently |
| Last check failed | Previously fetched information remains readable, but the refresh failed |

That distinction is central to the implementation. A network error should not erase a useful cache, and an unfamiliar version string should not become a false update notification.

The [version comparator](app/src/main/java/com/noise/appscout/domain/version/VersionComparator.kt) handles numeric components and prerelease ordering. The [status resolver](app/src/main/java/com/noise/appscout/domain/release/ReleaseStatusResolver.kt) combines that decision with installation and refresh state.

## The same refresh logic, wherever it starts

Foreground refresh and background work call shared domain use cases. GitHub responses are persisted in Room before the UI observes them. Conditional requests reuse cached releases through ETags; a release-list fallback handles cases where the latest-release endpoint cannot provide an eligible result.

Compose screens consume ViewModel state, Room owns the release cache, and DataStore holds preferences. This makes offline reading part of the normal data path. [Architecture](docs/architecture.md) · [Design decisions](docs/decisions/)

## Build and try

Use Android Studio with the project's configured toolchain:

```bash
./gradlew assembleDebug
./gradlew lint test
```

Attach a repository, refresh, read a release, then reopen the app offline. Existing JVM tests exercise HTTP/cache responses, version parsing, stale states, notifications, scheduling and AI validation. The [device acceptance test](app/src/androidTest/java/com/noise/appscout/TrackRealRepositoryTest.kt) also requires access to a public repository.

## Integration notes

Core tracking needs no Gemini key. Optional summaries send the app name and release text to Gemini; the key uses Android Keystore-backed encrypted storage. Structured-output checks bound responses, while a keyword heuristic filters some unsupported security claims. Summaries still require human judgment and do not replace the original notes.

Sources are linked manually and limited to GitHub releases. Unusual version schemes stay uncertain. Prerelease inclusion does not ensure the newest prerelease is discovered when the latest-release endpoint already returns an eligible stable release. Gemini model/service availability is an external dependency.

MIT licensed: [LICENSE](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md).
