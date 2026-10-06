# ADR-006: No automatic APK installation (and no APK downloading in MVP)

**Status:** Accepted · 2026-10

## Context

The natural temptation for an "update tracker" is to become an updater: fetch the APK asset from
the GitHub release and install it silently or with one tap. That path has hard constraints:
`REQUEST_INSTALL_PACKAGES` + user interaction per install, Play policy concerns, signature and
ABI mismatches (arm64/x86, split APKs), and the real risk of installing a build that does not
match the installed flavour. The spec marks automatic installation as an explicit non-goal.

## Decision

* AppScout is **read-only with respect to installation**. It never requests
  `REQUEST_INSTALL_PACKAGES`, never writes APKs to disk, and never invokes a package installer.
* Links from release details open the **release page in the browser** (`CustomTab`/`ACTION_VIEW`),
  where the user decides what to download and install.
* The permission set is limited to `INTERNET`, `ACCESS_NETWORK_STATE` and `POST_NOTIFICATIONS`;
  `<queries>` declares only `MAIN`/`LAUNCHER` instead of `QUERY_ALL_PACKAGES`.

## Alternatives

1. Full in-app updater with `PackageInstaller` session API.
2. "Open APK in the system installer" after downloading the asset.
3. Silent/root install — not possible for a normal app.

## Trade-offs

* One extra hop (app → browser → installer) for the user.
* We cannot verify that the user actually updated; the installed version is re-read from
  `PackageManager` on each refresh.

## Consequences

* No elevated permissions, no untrusted file handling, no signature/ABI mistakes.
* The permission manifest stays minimal and reviewable for a public GitHub repo.
* Update detection stays honest: status is recomputed from what is actually installed.
