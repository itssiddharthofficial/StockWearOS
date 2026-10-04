# Wear OS Stock Ticker — Project Specification

**App name:** Stocks (working title)
**Target device:** Circular Wear OS smartwatch, 466×466px AMOLED (OnePlus Watch 2R form factor)
**Platform:** Wear OS 4 (API 34), min API 30 (Wear OS 3)
**Primary language:** Kotlin + Jetpack Compose for Wear OS
**Backend:** Python (Flask) proxy over Yahoo Finance, or direct on-device fetch — both paths documented below
**Audience for this document:** a developer or AI coding agent building the app from scratch, with no other context

---

## 1. Project overview

A watch-native stock ticker for Indian markets (NSE/BSE), built specifically for a circular Wear OS display. The user keeps a small watchlist (default 5 symbols), glances at a home screen showing live price change, taps into a stock for a detailed ring-based view with fundamentals, and can search and swap symbols in and out of the watchlist. Everything is read-only market data — no trading, no order placement, no brokerage account linkage.

The defining constraint is the display: a 466×466px circle with no corners. Every screen, list, and control must be designed radially, not as a cropped rectangle. The secondary constraint is battery/CPU: a watch app that syncs too aggressively or keeps the screen busy will be uninstalled.

Design priorities, in order: (1) never crash, (2) legible at a glance in under 2 seconds, (3) feel fast/native, (4) look premium (flat dark UI, no visual clutter).

---

## 2. Features

### Must-have (v1)
- Home screen: default 5-stock watchlist shown as circularly-arranged tappable bubbles, each with symbol + % change color-coded green/red, plus a center element showing aggregate portfolio % change and the time.
- Stock detail screen: circular ring indicator with stock name, last price, and % change rendered inside the ring; a 2×2 metrics grid below (market cap, P/E, dividend yield, 52-week high); scrollable panel underneath with returns (1Y/3Y/5Y), shareholding pattern (FII/DII/Promoter), and basic risk stats (beta, volatility).
- Swipe/tap Prev–Next navigation between watchlist stocks from inside the detail screen, without returning to the home screen.
- Circular slider control on the detail screen to switch the return/time-period window (e.g., 3M / 1Y / 5Y / 10Y), implemented as a rotary drag arc, not a linear slider.
- Search screen: full-circular search box + live-filtered results list against NSE/BSE symbols, each result showing current watchlist slot usage (e.g., "4/5"); tapping a result adds it to the watchlist if a slot is free.
- Watchlist management: long-press a home-screen bubble to remove it; hard cap of 5 symbols.
- Background price sync every 5 minutes via WorkManager, with Room as the local cache so the UI always has something to show even with no network.
- Full loading / empty / error / offline states on every screen — no infinite spinners, no blank screens.

### Nice-to-have (v2+, see §23 Future enhancements)
- Price alerts (push notification when a symbol crosses a threshold).
- Nifty 50 / Sensex complication for the watch face.
- Multiple watchlists / folders.
- Light theme toggle.
- Tiles (Wear OS Tile API) for glanceable portfolio summary without opening the app.

---

## 3. Prerequisites

Before starting, have the following ready:

- A computer capable of running Android Studio (8GB+ RAM minimum, 16GB recommended; ~10GB free disk for SDK + emulator images).
- A Google account (for Android Studio sign-in and Play Console, if publishing later).
- Basic familiarity with Kotlin and Jetpack Compose. Familiarity with Wear OS Compose specifically is helpful but not required — this spec calls out the Wear-specific APIs explicitly.
- A circular Wear OS device for final testing is strongly recommended but not required to start — the Wear OS emulator (round skin) covers most of the implementation and testing cycle. The OnePlus Watch 2R itself is Wear OS 4 based, API 34.
- If using the Flask backend path: Python 3.10+ installed locally, and a place to deploy it (Render, Railway, Fly.io, or a home server) if you want the watch app to hit it over the public internet rather than only on local Wi-Fi.
- No paid API keys are required anywhere in this spec (see §9).

---

## 4. Required software and SDK versions

| Component | Version |
|---|---|
| Android Studio | Ladybug (2024.2) or newer |
| Android Gradle Plugin | 8.5+ |
| Gradle | 8.7+ |
| Kotlin | 1.9.24+ (2.0.x acceptable) |
| Compose BOM | 2024.09.00+ |
| Wear Compose | 1.4.0+ (`androidx.wear.compose:compose-material`, `compose-foundation`, `compose-navigation`) |
| Horologist (optional helper libs) | 0.6.x |
| compileSdk / targetSdk | 34 (Wear OS 4) |
| minSdk | 30 (Wear OS 3) — see note below |
| JDK | 17 (bundled with recent Android Studio) |
| Python (backend, optional) | 3.10+ |
| Flask | 3.0+ |
| yfinance | 0.2.4x+ |

**minSdk note:** API 30 covers Wear OS 3 devices (Galaxy Watch 4+, Pixel Watch 1, OnePlus Watch 2 family). If you only care about the OnePlus Watch 2R and newer, you could raise minSdk to 33, but 30 costs nothing and widens compatibility — keep it unless you have a specific reason to raise it.

---

## 5. Recommended development environment

- **IDE:** Android Studio (not VS Code — Wear OS emulator management, layout inspector for round screens, and Compose previews are first-class only in Android Studio).
- **Emulator:** Create a Wear OS emulator with a **round** skin matching 466×466 (use the "Wear OS Large Round" or a custom AVD at 454x454/466x466 depending on available skins — close enough skins are fine since density handles the rest). API level 34 system image.
- **Physical device debugging:** Enable Developer Options and ADB debugging on the watch (Settings → System → About → tap Build number ×7), then either:
  - USB debugging if the watch has a cradle/cable with data lines, or
  - Wireless debugging: `adb pair <ip>:<port>` then `adb connect <ip>:<port>` over the same Wi-Fi network as the watch.
- **Version control:** Git, with the structure in §6 as the repo root.
- **Linting/formatting:** ktlint or Android Studio's built-in formatter; enable "Reformat on save" for Kotlin files.

---

## 6. Project structure

```
wear-os-stock-ticker/
├── WEAR_OS_STOCK_TICKER_SPEC.md        (this file)
├── backend/                             (optional Flask proxy — see §9)
│   ├── app.py
│   ├── requirements.txt
│   ├── config.py
│   ├── data_source.py
│   ├── search.py
│   ├── cache.py
│   └── utils.py
└── android/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle/wrapper/
    └── app/
        ├── build.gradle.kts
        ├── proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            ├── kotlin/com/siddharth/stockticker/
            │   ├── StockTickerApp.kt            (Application class, WorkManager init)
            │   ├── MainActivity.kt              (single Activity, hosts NavHost)
            │   ├── navigation/
            │   │   └── NavGraph.kt
            │   ├── ui/
            │   │   ├── home/
            │   │   │   ├── HomeScreen.kt
            │   │   │   └── HomeViewModel.kt
            │   │   ├── detail/
            │   │   │   ├── DetailScreen.kt
            │   │   │   └── DetailViewModel.kt
            │   │   ├── search/
            │   │   │   ├── SearchScreen.kt
            │   │   │   └── SearchViewModel.kt
            │   │   ├── components/
            │   │   │   ├── RingIndicator.kt      (the circular price ring)
            │   │   │   ├── CircularSlider.kt      (rotary time-period control)
            │   │   │   ├── StockBubble.kt
            │   │   │   ├── MetricCard.kt
            │   │   │   ├── LoadingState.kt
            │   │   │   ├── ErrorState.kt
            │   │   │   └── EmptyState.kt
            │   │   └── theme/
            │   │       ├── Color.kt
            │   │       ├── Type.kt
            │   │       └── Theme.kt
            │   ├── data/
            │   │   ├── local/
            │   │   │   ├── StockDatabase.kt
            │   │   │   ├── StockEntity.kt
            │   │   │   ├── WatchlistEntity.kt
            │   │   │   └── StockDao.kt
            │   │   ├── remote/
            │   │   │   ├── StockApiService.kt     (Retrofit interface)
            │   │   │   ├── ApiClient.kt
            │   │   │   └── dto/
            │   │   │       ├── StockDto.kt
            │   │   │       └── SearchResultDto.kt
            │   │   └── repository/
            │   │       └── StockRepository.kt
            │   ├── sync/
            │   │   └── PriceSyncWorker.kt         (WorkManager, 5-min periodic)
            │   └── util/
            │       ├── Formatters.kt              (currency, % formatting)
            │       └── ConnectivityObserver.kt
            └── res/
                ├── values/ (strings.xml, colors.xml, dimens.xml, themes.xml)
                ├── values-round/ (round-screen-specific dimens, if needed)
                ├── drawable/
                └── mipmap-*/ (launcher icons)
```

---

## 7. Dependencies

### `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") // Room codegen
}

android {
    namespace = "com.siddharth.stockticker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.siddharth.stockticker"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Core Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Wear OS specific
    implementation("androidx.wear:wear-tooling-preview:1.0.0")
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.19") // round-aware layout helpers

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### `backend/requirements.txt` (optional)

```
Flask==3.0.3
yfinance==0.2.43
requests==2.32.3
python-dotenv==1.0.1
Flask-Caching==2.3.0
gunicorn==22.0.0
```

---

## 8. API setup

There are two viable architectures. Pick one; the spec below documents both so either can be handed to a coding agent.

### Option A — Flask proxy (recommended)

The watch calls your own small Flask API; Flask calls Yahoo Finance via `yfinance` and caches results. This is recommended because:
- `yfinance` is a Python library (scrapes/queries Yahoo's unofficial endpoints) — it does not run cleanly on-device in Kotlin.
- A server-side cache means 5 watches (or 5,000 users) don't each hammer Yahoo independently.
- You can rate-limit, retry, and reshape the response into exactly the DTO the watch expects, keeping the Kotlin side simple and stable even if Yahoo's raw schema changes.

Downsides: you must host something (even a free-tier Render/Railway service, or your own machine on a static IP / Tailscale for local-only use).

### Option B — Direct on-device fetch

The watch calls Yahoo's undocumented chart endpoints directly (e.g., `https://query1.finance.yahoo.com/v8/finance/chart/RELIANCE.NS`) over OkHttp/Retrofit, no backend at all.

Downsides: no caching layer (battery/data cost on every open), you're fully exposed to Yahoo changing or blocking the endpoint with no server-side place to patch it, and CORS/User-Agent quirks are more annoying to handle from a mobile HTTP client than from Python. Acceptable for a personal/hobby build; not recommended if you want resilience.

**This spec assumes Option A (Flask proxy)** for the rest of the document, since it's the sturdier default — but the Kotlin `StockApiService` interface is written against clean REST endpoints either way, so swapping to Option B later only changes `data/remote/`, nothing in the UI layer.

---

## 9. Yahoo Finance integration

- No official Yahoo Finance API exists anymore (the old one was shut down in 2017). `yfinance` works by querying Yahoo's internal endpoints used by the finance.yahoo.com website itself. This is free, requires no API key, and no signup — but it is unofficial and technically against Yahoo's terms of service for heavy/commercial use. For a personal project with a handful of symbols polled every 5 minutes, this is low-risk and widely used by hobbyists; do not represent this app as using an "official" Yahoo API anywhere in app copy.
- **Rate limits:** undocumented and enforced informally (IP-based throttling/temporary blocks if hit too hard). Mitigate by:
  - Server-side caching with a 5-minute TTL per symbol (matches the app's own sync interval — no point fetching fresher than the UI shows).
  - Batching: `yfinance.Tickers("RELIANCE.NS TCS.NS HDFC.NS INFY.NS SBIN.NS")` fetches multiple symbols in fewer round trips than one call per symbol.
  - A simple in-memory or Redis cache in Flask (see `cache.py`) with jittered backoff on 429/5xx responses.
- **Symbol format:** NSE symbols use a `.NS` suffix (e.g., `RELIANCE.NS`), BSE uses `.BO` (e.g., `500325.BO`). Indices: Nifty 50 is `^NSEI`, Sensex is `^BSESN`.
- **Alternatives if Yahoo becomes unreliable:** NSE's own (also unofficial) JSON endpoints (`nseindia.com/api/...`, requires session cookies and realistic headers to avoid 403s), or paid options like Alpha Vantage (free tier: 25 req/day — too low for this use case), Twelve Data (free tier: 800 req/day), or the NSE-focused `nsetools`/`nsepy` Python packages. Document this as a fallback, not a default.
- **What the backend fetches per symbol:** current price, previous close (to compute % change), market cap, trailing P/E, dividend yield, 52-week high/low, and historical closes for 1Y/3Y/5Y return calculation (`ticker.history(period="5y")`). Shareholding pattern (FII/DII/Promoter %) is **not** available from Yahoo Finance — source this from NSE's shareholding-pattern endpoint or omit/mock it in v1 and mark it clearly as a v2 item if no clean free source is wired up. Do not silently fabricate numbers in the shipped app.

### Backend endpoint contract

```
GET  /api/stocks/watchlist?symbols=RELIANCE.NS,TCS.NS,HDFC.NS,INFY.NS,SBIN.NS
GET  /api/stocks/<symbol>?period=1y          // period: 3m|1y|3y|5y|10y
GET  /api/search?q=<query>
GET  /api/health                              // for app startup connectivity check
```

Example `GET /api/stocks/RELIANCE.NS?period=1y` response:

```json
{
  "symbol": "RELIANCE.NS",
  "name": "Reliance Industries Ltd",
  "price": 3245.50,
  "previous_close": 3227.00,
  "change": 18.50,
  "change_percent": 0.57,
  "currency": "INR",
  "market_cap_label": "15.2T",
  "pe_ratio": 24.5,
  "dividend_yield": 2.3,
  "week52_high": 3450.00,
  "week52_low": 2550.00,
  "period": "1y",
  "period_return_percent": 18.5,
  "updated_at": "2026-10-04T05:20:00Z"
}
```

Flask should return a `504`-style structured error (not a raw stack trace) on upstream Yahoo failure:

```json
{ "error": "upstream_unavailable", "message": "Could not reach data source", "symbol": "RELIANCE.NS" }
```

---

## 10. Wear OS UI/UX guidelines

- Use **Wear Compose Material** components (`androidx.wear.compose.material`) — `Chip`, `ScalingLazyColumn`, `Vignette`, `PositionIndicator`, `TimeText` — not the phone-oriented `androidx.compose.material3` package. They're built for round screens and small touch targets.
- Use `ScalingLazyColumn` (not `LazyColumn`) for any vertically scrolling list/content on the detail screen — it auto-scales and fades items near the circular edge so content doesn't look cut off, and it integrates with `PositionIndicator`.
- Every scrollable screen gets a `PositionIndicator` (the small scroll track on the right edge) so the user knows there's more content without a scrollbar eating screen space.
- Wrap round-screen layouts in Horologist's `ScreenScaffold` / `AppScaffold` where possible — it centralizes time-text, vignette, and scroll-state wiring instead of hand-rolling it per screen.
- Minimum touch target: 48dp×48dp (platform-standard), even though the whole screen is small — don't shrink tap targets below this to fit more content; fit less content instead.
- Avoid modal dialogs that require precise small-button taps; prefer full-screen confirmation or swipe-to-dismiss patterns native to Wear.
- Navigation is swipe-based: back = swipe from left edge (handled automatically by `SwipeDismissableNavHost`), not a back button widget.
- No hover states (no mouse) — every interactive element needs a clear pressed/ripple state instead.

---

## 11. Circular display considerations

- **Safe content area:** treat the outer ~40px as a bezel-safe margin. On a 466×466 screen, keep primary text and tap targets within a centered ~386×386 usable area; it's fine for background color/rings to bleed to the true edge, but never place text or controls there.
- **No rectangular cards with hard corners touching the edge.** Any card/list item should either be inset with equal margin on all sides, or explicitly shaped (rounded/circular) to read correctly against the curve.
- **Radial layouts for multi-item screens.** The home screen's 5 stock bubbles are arranged around a circle (polar coordinates: `x = cx + r·cos(θ)`, `y = cy + r·sin(θ)`), not a vertical list — a vertical list of 5 items wastes the left/right screen area and seats items that look fine in a rectangle awkwardly in a circle.
- **Lists that must scroll** (the detail screen's metrics/fundamentals panel) should still respect the curve: `ScalingLazyColumn`'s built-in edge-scaling handles this — items shrink and fade as they approach top/bottom of the visible circular area rather than being abruptly clipped.
- **Test on an actual round emulator skin**, not a square one with a circular mask overlaid in a screenshot — square-emulator testing hides edge-clipping bugs that only show up on real round hardware.
- Use `LocalConfiguration.current.isScreenRound` (or Horologist's screen-shape utilities) at runtime to branch layout logic if you ever support both round and the rare square Wear device — for this project, round-only is an acceptable simplification given the explicit target device, but don't hardcode pixel-perfect 466 values; use `Dp`/percentage-of-screen math so the layout degrades gracefully on a 450px or 480px round screen too.

---

## 12. Dark theme design

The app is dark-only in v1 (no light theme, no system-theme following) — this matches AMOLED power savings (true blacks draw less power) and the reference screenshots driving this spec.

**Palette:**

| Token | Hex | Usage |
|---|---|---|
| Background | `#0A0A0A` | Screen background (near-black, not pure `#000` to avoid OLED smear on some panels — but `#000000` is also acceptable if targeting AMOLED specifically for max battery savings) |
| Surface | `#1A1A1A` | Cards, metric tiles |
| Border / divider | `#333333` | Hairlines, card outlines |
| Text primary | `#E2E8F0` | Headlines, prices |
| Text muted | `#888888` | Labels, secondary text |
| Success / positive | `#22C55E` | Positive % change, up indicators |
| Danger / negative | `#EF4444` | Negative % change, down indicators |
| Accent / info | `#3B82F6` | Interactive elements, search, links |

**Typography (scaled for a ~410dp logical round screen):**

| Style | Size | Weight | Use |
|---|---|---|---|
| Display | 18sp | 700 | Price in ring |
| Title | 16sp | 700 | Stock name/symbol |
| Body | 12sp | 500 | Metric values |
| Label | 10sp | 400 | Metric labels, muted text |
| Caption | 9sp | 400 | Timestamps, fine print |

Implement via Wear Compose's `Typography` object in `theme/Type.kt`, and apply a single `MaterialTheme` wrapper (Wear's, not Material3's) in `theme/Theme.kt` so every screen inherits colors/type consistently instead of hardcoding hex values in composables.

---

## 13. Error handling

Define one sealed result type used throughout the data layer so every screen handles failure uniformly:

```kotlin
sealed class Resource<out T> {
    data class Success<T>(val data: T, val isStale: Boolean = false) : Resource<T>()
    data class Error(val message: String, val cachedData: Any? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
```

Rules:
- **Network failure with cached data available:** show the cached data with a small "last updated 12 min ago" style indicator, not a blocking error screen. Staleness beats blankness.
- **Network failure with no cached data (first launch, no network):** show the `ErrorState` composable with a retry button and a one-line human-readable reason ("Can't reach the market data service" — never a raw exception message or stack trace on screen).
- **Partial failure** (4 of 5 watchlist symbols loaded, 1 failed): show the 4 that succeeded; show the 5th bubble in a visibly degraded/greyed state with a small retry affordance on tap, not an app-wide error.
- **Backend 5xx / timeout:** Retrofit + OkHttp should have an explicit timeout (e.g., 8s connect/read) and a single automatic retry with backoff before surfacing an error — don't let a hung request spin the loading state indefinitely.
- All exceptions in `StockRepository` are caught at the repository boundary and converted to `Resource.Error` — ViewModels and Composables should never need to catch raw `IOException`/`HttpException` themselves.
- Log errors (Timber or plain `Log.e`) but never crash on a malformed/missing field in the API response — parse defensively (nullable DTO fields with sane fallback display like "—" rather than crashing on null).

---

## 14. Loading / empty states

- **Loading (first load, no cache yet):** a centered circular progress indicator (`CircularProgressIndicator` from Wear Compose, it's already ring-shaped so it fits the aesthetic for free) with no layout shift once data arrives — reserve the space data will occupy.
- **Loading (refresh, cache already shown):** a small, unobtrusive indicator (e.g., a subtly pulsing dot near the time display) — never re-show a full-screen spinner over content the user can already see.
- **Empty watchlist** (user removed all 5 symbols): a centered prompt — "No stocks yet" + a tap target that opens Search directly — not a blank dark circle.
- **Empty search results:** "No matches for '<query>'" centered, no results list skeleton.
- **Watchlist full (5/5) when trying to add a 6th:** inline message at the top of the search results ("Watchlist full — remove a stock first"), don't let the tap silently no-op.

---

## 15. Performance and battery optimization

- **Sync interval:** 5 minutes via `WorkManager` `PeriodicWorkRequest` (WorkManager's practical minimum periodic interval is 15 minutes — see note below) with a `NetworkType.CONNECTED` constraint, so no work is attempted/batched when offline.

  > **Correction to the "every 5 minutes" requirement:** Android's `PeriodicWorkRequest` enforces a **minimum 15-minute** repeat interval — this is a platform-level constraint, not a library limitation, and cannot be configured lower. To approximate 5-minute freshness: (a) run the periodic worker at the 15-minute floor for passive background updates, and (b) do a foreground on-demand refresh (`OneTimeWorkRequest` or direct repository call) every time the user opens the app or the detail screen, so data is always fresh *when looked at*, independent of the background cadence. Document this tradeoff in-app only if asked — don't silently promise 5-minute background accuracy you can't deliver.

- **Batch network calls:** one `/api/stocks/watchlist?symbols=...` call for all 5 symbols rather than 5 separate requests, both from the worker and from `HomeViewModel`'s initial load.
- **Respect Doze/App Standby:** don't use `setExactAndAllowWhileIdle` alarms or foreground services to force more frequent updates — that's exactly the battery-drain pattern that gets Wear apps uninstalled. `WorkManager` already handles Doze-aware scheduling correctly; don't fight it.
- **Avoid continuous animations.** Any `rememberInfiniteTransition` (e.g., a pulsing "syncing" dot) should pause when the screen isn't visible (`DisposableEffect` tied to lifecycle) — don't animate off-screen composables.
- **Image/icon assets:** use vector drawables (`ImageVector`/`.xml`), not bitmaps, for all iconography — smaller APK, crisper on the dense round display, no decode cost.
- **Room as source of truth:** the UI observes Room via `Flow`, not the network response directly — this means a slow/failed network call never blocks the UI from showing last-known data instantly on screen open.
- **APK size budget:** stay under 45MB total. Enable `isMinifyEnabled` + `isShrinkResources` in the release build (already in the Gradle snippet in §7); avoid pulling in unused Horologist modules — only add the specific artifacts needed.
- **Avoid polling while a screen is backgrounded:** cancel/pause any in-flight manual refresh coroutine in `onPause`/`DisposableEffect` cleanup so navigating away mid-fetch doesn't waste radio time on a response nobody will see.

---

## 16. Security considerations

- This app only reads public market data — there's no login, no PII, no payment flow, so the security surface is small, but still:
- **No hardcoded secrets.** If the Flask backend ever needs an API key (e.g., if you add a paid fallback provider from §9), keep it server-side only, in an environment variable (`python-dotenv` / platform secret manager) — never embed any key in the Kotlin app, since APKs are trivially decompiled.
- **HTTPS only** for the backend (even on a free host like Render/Railway, TLS is provided by default) — never ship a release build pointed at a plain `http://` backend; use `usesCleartextTraffic="false"` in the manifest for release builds, with a debug-only network security config exception if you need `http://localhost` during development.
- **Backend input validation:** the `/api/search` and `/api/stocks/<symbol>` endpoints take user-influenced input (even if typed on a watch, it's still attacker-controllable if the API is public) — sanitize/validate the symbol format server-side (alphanumeric + `.`/`^` only) before passing to `yfinance`, and rate-limit the public endpoints (e.g., Flask-Limiter) if the backend is internet-exposed, to avoid your server being used as an open proxy for scraping Yahoo.
- **Room database:** contains no sensitive data (just cached public prices), so no encryption is required, but don't `allowMainThreadQueries()` in production — keep all DB access on `Dispatchers.IO` via coroutines/Flow as Room's KTX extensions already encourage.
- **ProGuard/R8:** enabled in release (see §7) — this also has the side benefit of mild obfuscation of your backend URL string; still, don't treat obfuscation as real protection — if the backend truly needs restricting, put real auth on it, not just hope nobody decompiles the APK.

---

## 17. Step-by-step setup instructions

```bash
# 1. Clone / create the repo
mkdir wear-os-stock-ticker && cd wear-os-stock-ticker
git init

# 2. Backend
mkdir backend && cd backend
python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt  # (create this file per §7 first)
# create app.py, config.py, data_source.py, search.py, cache.py per §9 contract
python app.py                    # runs on http://localhost:5000 by default
# sanity check:
curl "http://localhost:5000/api/health"
curl "http://localhost:5000/api/stocks/watchlist?symbols=RELIANCE.NS,TCS.NS,HDFC.NS,INFY.NS,SBIN.NS"
cd ..

# 3. Android project
# Open Android Studio → New Project → "Wear OS" → "Blank Activity (Wear OS)"
# Package name: com.siddharth.stockticker
# Minimum SDK: API 30
# Replace generated files with the structure in §6, add dependencies from §7

# 4. Point the app at your backend
# In data/remote/ApiClient.kt, set BASE_URL:
#   - emulator talking to a backend running on your dev machine: http://10.0.2.2:5000/
#   - physical watch on the same Wi-Fi as your dev machine: http://<your-machine-LAN-IP>:5000/
#   - deployed backend: https://your-app.onrender.com/

# 5. Build & run
# Android Studio: select a Wear OS round emulator (API 34) → Run ▶
# Or from CLI:
cd android
./gradlew installDebug
adb shell am start -n com.siddharth.stockticker/.MainActivity
```

---

## 18. Development roadmap

**Phase 1 — Backend (0.5–1 day)**
- Flask app skeleton, `/api/health`, `/api/stocks/<symbol>`, `/api/stocks/watchlist`, `/api/search`.
- `yfinance` integration + in-memory TTL cache (5 min).
- Manual testing via curl/Postman against real NSE symbols.

**Phase 2 — Android project scaffold (0.5 day)**
- New Wear OS Compose project, package structure from §6, all dependencies resolving, empty `MainActivity` showing a "Hello" `TimeText` on a round emulator.

**Phase 3 — Data layer (1 day)**
- Room entities/DAO, Retrofit service + DTOs, `StockRepository` with the `Resource<T>` wrapper from §13, Moshi parsing with defensive nullable fields.

**Phase 4 — Home screen (1 day)**
- Radial 5-bubble layout (`StockBubble` component), center portfolio-% element, `TimeText`, search/add affordance, long-press-to-remove.
- Wire to `HomeViewModel` observing Room `Flow`.

**Phase 5 — Detail screen (1–1.5 days)**
- `RingIndicator` component (name/price/%-change inside the ring), 2×2 `MetricCard` grid, `ScalingLazyColumn` for scrollable fundamentals section.
- Prev/Next navigation between watchlist symbols without returning home.
- `CircularSlider` rotary control wired to re-fetch/re-render the return period.

**Phase 6 — Search screen (0.5 day)**
- Search input + live-filtered `ScalingLazyColumn` results, slot-count display, add-to-watchlist action with the 5-slot cap enforced.

**Phase 7 — Background sync (0.5 day)**
- `PriceSyncWorker` (`WorkManager`, 15-min floor per §15), network-connected constraint, writes straight to Room so the UI updates reactively with no extra plumbing.

**Phase 8 — States & polish (1 day)**
- `LoadingState`/`ErrorState`/`EmptyState` composables wired into every screen per §13/§14.
- Dark theme tokens applied consistently (no hardcoded hex in composables — everything through `MaterialTheme.colors`).
- Haptics on tap/long-press (`LocalHapticFeedback`), subtle enter/exit transitions between screens.

**Phase 9 — Testing & hardening (1 day)**
- Manual pass on round emulator + physical OnePlus Watch 2R if available.
- Airplane-mode test (cached data should still render).
- Kill-and-reopen test (cold start should show cache instantly, then refresh).
- APK size check, R8/ProGuard release build smoke test.

**Total estimate:** ~6–8 focused working days for a single developer building v1 end-to-end.

---

## 19. Testing on Wear OS emulator

1. Android Studio → Device Manager → Create Device → "Wear OS" category → pick a **round** skin (e.g., "Wear OS Large Round") → system image API 34.
2. Launch the AVD, wait for full boot (Wear OS emulators boot slower than phone AVDs — 1–2 min is normal).
3. Run the app from Android Studio (▶) targeting the Wear emulator.
4. Use the emulator's extended controls (`...` panel) to simulate: rotary input (if testing bezel/crown scroll), airplane mode (for offline-state testing), and location (irrelevant here, but useful to know it's there).
5. For backend connectivity from the emulator, use `10.0.2.2` to reach your host machine's `localhost` (standard Android emulator loopback alias) — not `127.0.0.1`, which inside the emulator refers to the emulator itself.
6. Layout Inspector (Android Studio → Tools → Layout Inspector) works on the Wear emulator and is useful for verifying nothing is clipped by the round mask.

---

## 20. Testing on a physical watch

1. On the OnePlus Watch 2R (or equivalent Wear OS 4 device): Settings → System → About → tap "Build number" 7× to unlock Developer Options.
2. Settings → Developer Options → enable "ADB debugging" and "Debug over Wi-Fi" (wireless is the realistic path — most watches don't expose a data-capable USB port for `adb` directly).
3. On the same Wi-Fi network as your dev machine:
   ```bash
   adb pair <watch-ip>:<pairing-port>   # code shown on the watch's pairing screen
   adb connect <watch-ip>:<debug-port>
   adb devices                           # confirm the watch shows up
   ```
4. Run/install from Android Studio with the physical device selected, or:
   ```bash
   cd android && ./gradlew installDebug
   ```
5. If testing against a locally-run Flask backend, point `BASE_URL` (per §17 step 4) at your dev machine's LAN IP, and ensure both devices stay on the same network/subnet (no VPN splitting them).
6. Validate real-world specifics only a physical device can show: actual AMOLED black levels, real touch-target comfort at true 466px density, battery drain over a few hours with the sync worker active, and haptic feedback feel.

---

## 21. Build & APK generation instructions

**Debug build (for sideloading/testing):**
```bash
cd android
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Release build (signed, minified):**
```bash
# 1. Generate a signing key (once)
keytool -genkey -v -keystore stockticker-release.keystore -alias stockticker \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. Configure signing in app/build.gradle.kts (signingConfigs block) referencing
#    the keystore path + passwords via gradle.properties (never commit these)

# 3. Build
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk

# 4. (Optional) Android App Bundle for Play Store distribution
./gradlew bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```

Keep `stockticker-release.keystore`, its passwords, and `gradle.properties` (if it holds signing credentials) **out of version control** — add them to `.gitignore` immediately.

---

## 22. Common errors and troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Emulator shows a square screen with the app content cropped weirdly | Wrong AVD skin selected | Recreate the AVD choosing a **round** Wear OS skin, not the default square one |
| `Cleartext HTTP traffic not permitted` crash | Release/manifest blocking plain `http://` | Use `https://` for any non-localhost backend, or add a debug-only Network Security Config exception for local dev |
| App can't reach backend from emulator | Used `localhost`/`127.0.0.1` instead of `10.0.2.2` | Point `BASE_URL` at `10.0.2.2` (emulator-to-host alias) |
| App can't reach backend from a physical watch | Watch and dev machine on different networks, or backend bound to `127.0.0.1` only | Run Flask with `app.run(host="0.0.0.0")`, confirm both devices share a Wi-Fi network, use the machine's LAN IP |
| `WorkManager` periodic sync "not running every 5 minutes" | Platform enforces a 15-minute floor for periodic work | This is expected (see §15) — rely on foreground refresh-on-open for sub-15-min freshness, not the background worker alone |
| Layout clipped at the screen edge on real hardware but fine in a square emulator preview | Content placed outside the circular safe area | Re-check margins against §11; test on an actual round skin, not a Compose `@Preview` with a square canvas |
| `yfinance` intermittently returns empty/`None` fields | Yahoo rate-limiting or transient upstream hiccup (expected with an unofficial API) | Ensure Flask's cache/retry logic (§9, §13) is in place; the Kotlin side should already degrade to cached/error state gracefully rather than crash |
| App icon/launcher missing on the watch's app list | Missing or malformed `mipmap` / round icon resources | Ensure both standard and round launcher icon variants are provided per Wear OS launcher icon guidelines |
| Room schema crash after changing an `Entity` | No migration provided for a schema version bump | Either increment `exportSchema`/provide a `Migration`, or (debug-only) use `fallbackToDestructiveMigration()` while iterating pre-release |
| Gradle sync fails on `ksp`/Room compiler version mismatch | KSP plugin version not aligned with Kotlin version | Pin the KSP plugin version to match your Kotlin version per the [KSP compatibility table](https://github.com/google/ksp/releases) |

---

## 23. Future enhancements

- **Price alerts:** local notification when a watchlist symbol crosses a user-set threshold; requires a slightly more frequent background check or a push-based approach (e.g., backend-side alert evaluation + FCM push) rather than relying solely on the 15-minute WorkManager floor.
- **Wear OS Tile:** a glanceable portfolio-summary Tile (swipe-accessible from the watch face) using the Tiles API, so the user doesn't need to open the full app for a quick check.
- **Watch face complication:** Nifty 50 / Sensex index value as a complication data source.
- **Multiple watchlists:** beyond the single 5-symbol cap, support named lists (e.g., "Core", "Watching") — would require relaxing the hard 5-item UI assumption on the home screen's radial layout.
- **Light theme:** currently dark-only by design (§12); could add a theme toggle later without major architecture change since color tokens are already centralized.
- **Proper shareholding-pattern data source:** v1 may omit or mock FII/DII/Promoter % (noted as unavailable from Yahoo in §9) — a v2 could integrate an NSE-specific scraper/endpoint with appropriate session/header handling.
- **Voice input for search** using the Wear OS platform's built-in speech-to-text intent, since typing on a round keyboard is the worst part of any watch UX.
- **Offline-first smarter sync:** adaptive sync interval (more frequent during Indian market hours 9:15am–3:30pm IST, paused entirely outside market hours and on weekends) to cut background cost further than the flat 15-minute floor already does.

---

*End of specification.*
