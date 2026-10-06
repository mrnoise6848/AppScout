# ADR-002: ViewModel + StateFlow + unidirectional data flow

**Status:** Accepted · 2026-10

## Context

The spec requires ViewModel + StateFlow + UDF. The screens also have to be previewable and
testable in instrumentation tests without booting Hilt, Room or the network stack.

## Decision

* One `@HiltViewModel` per screen exposing a single `StateFlow<UiState>`.
* Screens are **stateless composables**: `(state, callbacks) -> Unit`. A thin `…Route` wrapper
  obtains the ViewModel (`hiltViewModel()`) and collects with `collectAsStateWithLifecycle()`.
* All mutations are intent functions on the ViewModel (`refresh()`, `setNotificationsEnabled()`);
  composables never write state directly.
* Derived data (sections, counts, error flags) is computed in the ViewModel so rendering is dumb.
* Emissions are conflated via `SharingStarted.WhileSubscribed(5_000)` so a backgrounded screen
  stops doing work while `HomeViewModel.refresh()` keeps running only while observed.

## Alternatives

1. `LiveData` (classic AndroidView way).
2. MVI library with an event loop / reducer + middleware.
3. State hoisted into composables with `rememberSaveable` only.

## Trade-offs

* `StateFlow` conflates intermediate values — we deliberately drop transient states rather than
  flash intermediate UI.
* Route wrappers add a file per screen, but they are what lets UI tests pass plain data.
* One big `UiState` can grow; we split state per screen instead of a global store.

## Consequences

* UI tests instantiate `HomeScreen(state = …)` directly — no Hilt test runner needed.
* Preview functions render real states, which keeps screenshots and layout review honest.
* No event bus: navigation and one-shot effects (snackbars) are callbacks/`LaunchedEffect` on
  explicit state, so nothing is emitted into the void.
