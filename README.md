# Weather App (Wetter)

Native Android weather application written in Kotlin and Jetpack Compose. It shows live
weather data from [Open-Meteo](https://open-meteo.com/) for a configurable home location and
any number of favourite locations, caches responses locally, and works offline as long as a
cached forecast exists.

All user-visible text is German; source code, comments and this documentation are English.

## Feature summary

| Area | Behaviour |
| --- | --- |
| Current weather | Temperature, condition, feels-like, today's min/max, humidity, precipitation, cloud cover, wind speed/direction/gusts, pressure. |
| Hourly (today) | Horizontal strip of the current day's hours, auto-scrolled to the current hour, the current hour is highlighted. Tapping an hour opens a "Stundendetails" bottom sheet with all values for that hour. |
| Days 1–2 | Two detailed cards ("Morgen", "Übermorgen") with condition, precipitation probability/amount, wind, gusts, sunshine and day length. |
| Days 3–15 | Compact list ("Ausblick") with condition icon, min/max temperature and a temperature range bar; up to the 16-day forecast horizon of the API. |
| Locations | A home location plus favourites. Search by name (Open-Meteo geocoding), add as favourite, remove, reorder with up/down arrows, promote a favourite to home, and tap any entry to display it. Duplicate places are rejected (same id or same rounded coordinates). Home is a single slot: promoting a favourite to home replaces the previous home entry. The last displayed location is restored on the next start. |
| Refresh | Pull-to-refresh, toolbar refresh button (both bypass the cache), automatic refresh when the app returns to the foreground, and automatic refresh when the visible data is older than the cache TTL. |
| Offline | The last response per location is cached; with no network the cached data is shown and labelled "Gespeicherte Daten" instead of an empty screen. |
| Appearance | Material 3, light and dark theme following the system setting, rounded cards, condition-dependent gradient header, custom vector weather icons, German date/number formatting, 24-hour time. |

## Data source

No API key is required. Only these two HTTPS endpoints are used:

- Forecast: `https://api.open-meteo.com/v1/forecast` with `timezone=auto` and
  `forecast_days=16` (current, hourly and daily variables).
- Geocoding: `https://geocoding-api.open-meteo.com/v1/search`.

WMO weather codes are mapped to a domain enum (`WeatherCondition`) which drives both the
German description and the vector drawable. Day/night variants are resolved with the API's
`is_day` flag. Unknown codes fall back to `UNKNOWN` — values are never invented; missing
values render as an en dash.

## Architecture

MVVM with unidirectional state flow. A small manual container (`di/AppContainer`, owned by
`WeatherApplication`) provides the singletons; no DI framework is used.

```
Composable screens (WeatherRoot, DashboardScreen, LocationManagerSheet, LocationSetupScreen)
        ▲  MainUiState (StateFlow)                      ▼ user events
   ui/MainViewModel  ── combines settings + weather flows, owns refresh scheduling
        ▲                                                    ▼
   domain/  WeatherFormatter, ForecastSections, CachePolicy, WeatherCondition, model/*
        ▲
   data/repository/  WeatherRepository (cache-first, network refresh),
   data/local/       LocationRepository (DataStore settings)
        ▲            ForecastCache (Room), SettingsStore (DataStore)
   data/remote/  Retrofit + OkHttp + kotlinx.serialization, ForecastMapper (DTO → domain)
```

Key decisions:

- **DTOs never reach the UI.** `ForecastMapper` converts `ForecastResponseDto` into domain
  models (`CurrentWeather`, `HourlyWeather`, `DailyWeather`, `WeatherSnapshot`) and skips
  rows whose timestamp cannot be parsed; hourly series are de-duplicated and sorted.
- **Local day bucketing.** "Today" is computed in the location's time zone, so a location
  on the other side of the planet gets its own calendar day, including DST transitions
  (23 h and 25 h days are handled).
- **Cache.** Room table `forecast_cache`, one row per location id, storing the raw response
  JSON plus `fetched_at_epoch_milli`. A cached entry younger than
  `CachePolicy.freshDuration` (30 min) is served without any network call; older data is
  shown immediately while a refresh runs. Failures never drop already displayed data:
  the snapshot stays and the failure is reported alongside it. HTTP/IO/serialisation errors
  map to a small `DataLoadFailure` set with German messages; corrupted cache rows are
  discarded.
- **Concurrency.** One load job per selection in the ViewModel, cancelled and restarted on
  location changes; a `isStillSelected()` guard prevents a late response from overwriting a
  newer selection. Request and persistence work runs on `Dispatchers.IO`.
- **Theme.** Static Material 3 palettes (no dynamic colour) so the gradient headers keep a
  guaranteed contrast ratio on every device. Card and sheet containers use explicit surface
  colours because the M3 elevation tint would otherwise tint the surfaces.
- **AGP 9.5.2 with built-in Kotlin.** The Kotlin plugin is not applied; the embedded Kotlin
  2.2.10 stdlib is force-pinned in `build.gradle.kts`, and dependency versions must stay
  compatible with it.

## Build and run

Android Studio (or a JDK 17+ and an Android SDK) is required. If `java` is not on `PATH` and
there is no `local.properties`, export the toolchain first:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

```powershell
.\gradlew.bat assembleDebug          # debug APK
.\gradlew.bat testDebugUnitTest      # unit tests (87)
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat connectedDebugAndroidTest   # instrumented + Compose UI tests on a running device
.\gradlew.bat installDebug           # install on the connected device/emulator
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

- **Unit tests** (`app/src/test`, deterministic, no network): WMO code mapping including
  day/night and unknown codes; German formatting (temperatures, negative zero, pressure
  grouping, DST clock times, German dates, durations, relative time, compass); forecast
  section building including other time zones, DST days and de-duplication; cache policy;
  DTO → domain mapping including truncated arrays, missing values, error payloads and
  corrupt cache JSON; `WeatherRepository` cache-first behaviour, force refresh, failure
  classification, partial payloads; `LocationRepository` home/favourite rules,
  de-duplication, reordering, selection fallbacks, search debouncing/short queries;
  `MainViewModel` state transitions (initial load, location switch, late responses,
  foreground refresh, cached-while-refreshing, error states).
- **Instrumented tests** (`app/src/androidTest`): Room cache store round-trip, replace,
  delete (in-memory database); Compose UI tests for the rendered dashboard, the hourly
  detail sheet, the error state with its retry action, the saved-locations sheet and the
  "Gespeicherte Daten" label for cached data.

## Manual verification performed

On a `Medium_Phone_API_37.0` emulator (API 37, 1080×2400, headless), with real API traffic:

- Dashboard in light **and** dark mode, scrolled sections, and in landscape (dark).
- First start with empty settings (setup screen), location search with live geocoding
  results, and setting the home location from a search result (dark).
- Live fetch after adding a location, including the correct local date/time of the location
  (Tokio: 21:00 while the device showed 13:00) and night icons in the hourly strip.
- Hourly detail sheet (light and dark), saved-locations sheet (light and dark) with
  reorder arrows, promote-to-home and remove actions, and the empty-favourites hint.
- Switching between locations, manual refresh via the toolbar (recovery to "Aktuelle Daten").
- Offline behaviour: with Wi-Fi/mobile data disabled the app was restarted and served the
  cached forecast labelled "Gespeicherte Daten"; pull-to-refresh then kept the data and
  showed "Keine Verbindung zum Wetterdienst. Angezeigt werden gespeicherte Daten."
- Persistence across force-stop and restart (home, favourites and last selection restored).
- Logcat: empty crash buffer, no `FATAL EXCEPTION` entries during any of the above.

Not verified manually: reordering across more than two favourites, low-API-level devices,
physical devices and tablets.

## Known limitations

- The app has no coarse/fine location permission: the home location is chosen by search
  (which is the configurable behaviour required by the product spec).
- Offline support depends on previously fetched data; a first start without network shows an
  empty/error state instead of fabricated data.
- Hourly detail is only available for the current day; the API only exposes hourly data
  inside the 16-day window, and days beyond 16 are not requested.
- Verified on the API 37 phone emulator only; no physical device, tablet, or low-API-level
  device testing was done.
- Lint reports only "newer version available" advisories for Gradle 9.6.0, core-ktx,
  Compose BOM, the Kotlin plugins and kotlinx-serialization. Upgrading the Kotlin plugins
  beyond 2.2.x is intentionally avoided while AGP's embedded Kotlin stdlib is pinned.
