# Third-party notices

AppScout itself is MIT licensed (see [`LICENSE`](LICENSE)). It uses the following open-source
libraries. Unless stated otherwise, each is licensed under the **Apache License 2.0**
(<https://www.apache.org/licenses/LICENSE-2.0>).

| Library | Group | License |
| --- | --- | --- |
| Kotlin standard library | org.jetbrains.kotlin | Apache-2.0 |
| KotlinX coroutines | org.jetbrains.kotlinx | Apache-2.0 |
| KotlinX serialization | org.jetbrains.kotlinx | Apache-2.0 |
| Jetpack Compose (BOM 2026.09.00, Material 3, UI, icons-core) | androidx.compose | Apache-2.0 |
| Activity Compose / Lifecycle Compose | androidx | Apache-2.0 |
| Navigation Compose | androidx.navigation | Apache-2.0 |
| Room (runtime, KTX, compiler, gradle plugin) | androidx.room | Apache-2.0 |
| DataStore Preferences | androidx.datastore | Apache-2.0 |
| WorkManager (runtime, Hilt, testing) | androidx.work | Apache-2.0 |
| Hilt / Dagger | com.google.dagger | Apache-2.0 |
| AndroidX Core KTX, Test, Espresso, JUnit | androidx | Apache-2.0 |
| Retrofit | com.squareup.retrofit2 | Apache-2.0 |
| OkHttp / MockWebServer / Okio | com.squareup.okhttp3, com.squareup.okio | Apache-2.0 |
| JUnit 4 | junit | EPL-1.0 |

Runtime destinations used by this app:

* `https://api.github.com` — GitHub REST API (subject to GitHub API Terms of Service)
* `https://generativelanguage.googleapis.com` — Google Gemini API, **only** if you enable AI
  summaries and provide your own API key (subject to Google AI terms)

AppScout ships no third-party binaries of its own, embeds no fonts or artwork from third parties,
and bundles no advertising, analytics or crash-reporting SDKs.
