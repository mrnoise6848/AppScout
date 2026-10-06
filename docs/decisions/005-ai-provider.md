# ADR-005: Optional, advisory AI behind a provider interface (BYOK Gemini)

**Status:** Accepted · 2026-10

## Context

Release notes are often long, informal or missing; a short summary helps a user decide whether to
update. But AI must never decide *for* the user, must be optional, must not require our own
backend, and must not store secrets in the repo. The spec makes AI advisory and non-authoritative
with an explicit output contract.

## Decision

* `AiSummaryProvider` is a domain-facing interface:
  `suspend fun summarize(release, app): AiSummaryResult`.
* Two implementations: `GeminiAiSummaryProvider` (real HTTP call to the Gemini REST API, BYOK) and
  `FakeAiSummaryProvider` (tests only — never shipped as a production path).
* The API key is entered by the user in Settings and stored in an **Android Keystore AES-GCM
  vault** (`core/security`); it never appears in DataStore, the database, logs or the repository.
* Responses are parsed with `kotlinx.serialization` and validated against the contract
  (`summary`, `why[]`, `importance`). Invalid/malformed output is discarded — never partially
  rendered as if it were valid.
* Result is cached per release in `ai_summaries` so summaries are computed once.
* Failure modes map to explicit UI states: disabled / no key / network failure / invalid output.
  All of them leave the raw release notes fully usable.

## Alternatives

1. On-device model — too heavy and inconsistent for MVP.
2. Our own proxy backend — forbidden (no backend) and would hold secrets.
3. Free-form "chatbot" UI over release notes — explicitly excluded by the spec.
4. Skipping the interface and calling Gemini from the ViewModel — untestable, couples UI to HTTP.

## Trade-offs

* Users must obtain their own Gemini key (friction) — accepted for a secret-free, backend-free MVP.
* Summaries cost the user's quota and depend on Google availability — hence optional.
* Structured output validation means some good-but-non-conforming answers are dropped.

## Consequences

* Core tracking works fully with AI off; nothing in `feature/` requires the provider.
* Provider behaviour (valid, malformed, error, no key) is unit-testable with a fake transport.
* The UI always labels AI output as advisory and shows the original notes underneath.
