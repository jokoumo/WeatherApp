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
| Hourly (rolling) | Horizontal strip with a rolling 24-hour window: it starts at the hourly interval that **contains** the current time, highlights it, labels every day the window touches ("Heute", "Morgen", weekday) and moves on its own when the hour changes. If that current hour is missing from the response, the earliest later hour starts the window; an already elapsed hour is never used as the start, and if the payload only contains elapsed hours the strip shows the unavailable-data note instead of outdated values. The window covers the current interval plus the following 23 hourly intervals by absolute timestamp, so a gap shortens the list rather than stretching the horizon. Tapping an hour opens a "Stundendetails" bottom sheet with all values for that hour. |
| Days 1–2 | Two detailed cards ("Morgen", "Übermorgen") with condition, precipitation probability/amount, wind, gusts, sunshine and day length. |
| Days 3–15 | Compact list ("Ausblick") with condition icon, min/max temperature and a temperature range bar; up to the 16-day forecast horizon of the API. |
| Locations | A home location plus favourites, managed on a dedicated full screen instead of a bottom sheet: the title and the search field stay fixed at the top, the saved locations live in a list that scrolls on its own, and search results appear in a floating dropdown directly under the search field, overlaying the list without moving it. A circular floating button in the bottom-left corner (above the navigation bar, hidden while the keyboard is open) returns to the dashboard; the system back button first closes the suggestions. Search by name (Open-Meteo geocoding), add as favourite, remove, reorder with up/down arrows, promote a favourite to home, and tap any entry to display it. Duplicate places are rejected (same id or same rounded coordinates) and reported without leaving the screen. Home is a single slot: promoting a favourite to home replaces the previous home entry. The last displayed location is restored on the next start. |
| Refresh | Pull-to-refresh, toolbar refresh button (both bypass the cache), automatic refresh when the app returns to the foreground, and a silent background refresh when the cached response belongs to a previous calendar day in the location's time zone. The hourly window itself advances every hour without any network request. |
| Offline | The last response per location is cached; with no network the cached data is shown and labelled "Gespeicherte Daten" instead of an empty screen. |
| Appearance | Material 3, light and dark theme following the system setting, rounded cards, condition-dependent gradient header, custom vector weather icons, German date/number formatting, 24-hour time. |

## Data source

No API key is required. Only these two HTTPS endpoints are used:

- Forecast: `https://api.open-meteo.com/v1/forecast` with `timezone=auto`,
  `timeformat=unixtime` and `forecast_days=16` (current, hourly and daily variables).
  `timeformat=unixtime` makes every `time` value an absolute Unix timestamp in seconds, so the
  app never has to guess a date-time string's offset.
- Geocoding: `https://geocoding-api.open-meteo.com/v1/search`.

WMO weather codes are mapped to a domain enum (`WeatherCondition`) which drives both the
German description and the vector drawable. Day/night variants are resolved with the API's
`is_day` flag. Unknown codes fall back to `UNKNOWN` — values are never invented; missing
values render as an en dash.

## Architecture

MVVM with unidirectional state flow. A small manual container (`di/AppContainer`, owned by
`WeatherApplication`) provides the singletons; no DI framework is used.

```
Composable screens (WeatherRoot, DashboardScreen, LocationManagerScreen, LocationSetupScreen)
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
- **Absolute timestamps.** Because the request uses `timeformat=unixtime`, `HourlyWeather`
  and `CurrentWeather` store an `Instant` — the start of the forecast interval — and the
  location's `ZoneId` is only used when a value is rendered. Local date-time strings are never
  used as identity or for sorting, so a daylight saving transition that repeats a clock hour
  (Europe/Berlin on 2026-10-25: 02:00 occurs twice) keeps both hours as separate entries, and a
  transition that skips an hour (2027-03-28: no 02:00) simply has no entry for it. Clock times
  that occur twice inside the window are rendered with their UTC offset ("02:00 +02" /
  "02:00 +01"). Zones with fractional offsets (+05:30, +05:45) work the same way.
- **Daily values are calendar data.** `daily.time` is a local midnight stamp, so the date is
  recovered with the response's own `utc_offset_seconds` (falling back to the location zone)
  instead of shifting the label by a UTC conversion — a 16-day outlook that spans a DST change
  stays on the right dates.
- **Missing observation time stays missing.** If the API omits or corrupts `current.time`,
  `CurrentWeather.instant` is `null`; the app never substitutes the device clock. The header
  then shows "Messzeitpunkt nicht verfügbar" instead of a misleading timestamp.
- **Rolling hourly window.** `ForecastSections.buildForecastSections(snapshot, referenceInstant)`
  selects the 24 intervals from the current hour onwards by absolute instant — never by local
  hour number or calendar date — so a window may cross midnight or a DST day of 23/25 hours and
  still show 24 consecutive real hours. `WeatherRoot` re-renders the window once per local hour
  (`millisUntilNextHour`) and on resume; a missing record shortens the window instead of being
  padded with invented values.
- **Local day bucketing.** "Today" is computed in the location's time zone, so a location
  on the other side of the planet gets its own calendar day, including DST transitions
  (23 h and 25 h days are handled). The hero card's min/max still uses the whole local day,
  independent of the rolling window.
- **Cache.** Room table `forecast_cache`, one row per location id, storing the raw response
  JSON plus `fetched_at_epoch_milli`. An entry is served without a network call only if it is
  (a) not from the future and at most `CachePolicy.freshDuration` (30 min) old, (b)
  downloaded on the current calendar day **in the location's time zone**, and (c) still covers
  the current hour. Future-dated entries are stale. Older data is shown immediately while a
  refresh runs. Failures never drop already displayed data: the snapshot stays and the failure
  is reported alongside it, and usable cache rows are never deleted. HTTP/IO/serialisation
  errors map to a small `DataLoadFailure` set with German messages; corrupted cache rows are
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
.\gradlew.bat testDebugUnitTest      # unit tests (145)
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat connectedDebugAndroidTest   # 21 instrumented + Compose UI tests on a running device
.\gradlew.bat installDebug           # install on the connected device/emulator
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

- **Unit tests** (`app/src/test`, deterministic, no network, fixed clocks and timestamps):
  WMO code mapping including day/night and unknown codes; German formatting (temperatures,
  negative zero, pressure grouping, DST clock times, clock time with UTC offset, German dates,
  durations, relative time, compass); the rolling hourly window (13:25, 20:15, 23:45 and 00:10
  selection, hour boundaries, no elapsed hours, sorted and unique entries, gaps without
  fabricated hours, a window truncated by the end of the data, other time zones, the clock-tick
  delay including a 30 minute shift); **start-interval selection** (`HourlyWindowStartSelectionTest`,
  17 tests:
  the interval containing the reference instant wins, a missing current hour falls forward to the
  earliest later hour, elapsed-only payloads produce an empty window instead of stale hours, a
  payload that starts later is shown from its first point, the exact hour boundary one second
  before and one second after, empty and unsorted payloads, a multi-hour gap that shortens the
  window to 19 entries inside a 24-hour horizon instead of stretching it, crossing midnight,
  the Berlin spring and autumn transitions, the second occurrence of a repeated local hour
  becoming current later, a half-hour offset zone such as Asia/Kolkata, and the raw
  `hourlyWindowStartIndex` / `hourlyWindow` contract); daylight saving specifics (Europe/Berlin
  autumn with both
  02:00 occurrences and their offsets, the spring day without 02:00, America/New_York,
  +05:30 and +05:45 zones); cache policy (TTL, local day rollover, location zone vs fixed zone,
  23 h and 25 h days, future-dated entries, hourly coverage); DTO → domain mapping with Unix
  timestamps, legacy ISO rows, daily dates recovered through `utc_offset_seconds`, truncated
  arrays, missing values, error payloads and corrupt cache JSON; `WeatherRepository`
  cache-first behaviour, day-boundary invalidation, force refresh, failure classification,
  partial payloads; `LocationRepository` home/favourite rules, de-duplication, reordering,
  selection fallbacks, search debouncing/short queries; `MainViewModel` state transitions
  (initial load, location switch, late responses, hourly tick without extra request, silent
  refresh after a local day change, foreground refresh, cached-while-refreshing, error states).
- **Instrumented tests** (`app/src/androidTest`): Room cache store round-trip, replace,
  delete (in-memory database); Compose UI tests for the rendered dashboard, the hourly strip
  starting at the current hour with its day label, the hourly detail sheet, the "no usable
  observation time" label, the error state with its retry action, the "Gespeicherte Daten"
  label for cached data, and the toolbar opening the **full screen location manager**
  (`LocationManagerScreenTest`, 10 tests): the manager shows its own title, the fixed search
  field and the saved places; the field and title stay in place while a 25 favourite list is
  scrolled and the home entry scrolls out with the list only; the last favourite can be
  scrolled clear of the floating back button and the button keeps at least a 48 dp touch
  target (bounds compared, no fixed pixel values); suggestions overlay the favourites without
  moving them; choosing a suggestion adds the favourite, clears the query and closes the
  dropdown while staying on the location screen; the same place cannot be added twice;
  duplicate feedback is shown on the manager itself; back closes the suggestions first and the
  screen only afterwards; the floating button returns to the dashboard; and an empty favourite
  list still keeps the search field usable.

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
- Rolling hourly strip with live data at 15:49 local: the strip is labelled "Nächste 24
  Stunden / So., 22. November", the highlighted first chip is the current hour
  ("Heute", 15:00) followed by 16:00, 17:00, 18:00, and scrolling reaches the next calendar
  day (03:00–06:00) with no duplicated or missing hours.
- Hourly values compared against a direct Open-Meteo query for the same location:
  15:00 → 5 °C / 3 %, 23:00 → 2 °C / 3 %, 00:00 → 2 °C / 5 %, 01:00 → 2 °C / 3 %,
  02:00 → 2 °C / 0 % (identical to the chips), so the Unix timestamps stay index-aligned
  with the value arrays across local midnight.
- The single day label "Morgen" appears on the 00:00 chip only (23:00 and 01:00 show an
  empty label line, so the chips stay vertically aligned); the night/day icons and the
  `is_day` flags agree for those hours.
- Hourly detail sheet for an hour that belongs to the next calendar day, titled
  "Stundendetails / Mo., 23. November · 00:00" with all values of that hour.
- Cross-time-zone window: with the device at 15:43 CET, switching to Tokio showed the
  header "Sonntag, 22. November 2026, 23:30", the strip starting at the Tokio current hour
  ("Heute", 23:00) and rolling to "Morgen", 00:00 – that is, day labels follow the
  **location's** date while the device was still on the previous day.
- Observation timestamp: the header showed "15:45" (the API's observation stamp) while the
  device clock read 15:49, i.e. the payload timestamp is displayed rather than the fetch time.
- Automatic window advance **without restarting the app or pulling to refresh**: the strip was
  captured at 15:49 (first chip "Heute", 15:00) and again at 16:01, where the first chip was
  "Heute", 16:00 followed by 17:00–19:00, the elapsed 15:00 hour had disappeared, and the
  timestamp line still read "Zuletzt aktualisiert: vor 10 Minuten" with the unchanged
  observation time "15:45" – the window moved on the cached payload, no second request was sent.
- Offline behaviour: with Wi-Fi/mobile data disabled the app was restarted and served the
  cached forecast labelled "Gespeicherte Daten"; pull-to-refresh then kept the data and
  showed "Keine Verbindung zum Wetterdienst. Angezeigt werden gespeicherte Daten."
- Persistence across force-stop and restart (home, favourites and last selection restored).
- Logcat: empty crash buffer, no `FATAL EXCEPTION` entries during any of the above.

### Location management screen and hourly start (current iteration)

Same emulator, real API traffic, screenshots in `app/build/verify/`:

- Full screen manager in **light and dark** theme: "Orte verwalten" with its hint, the search
  field and the "Gespeicherte Orte" group label stay fixed while the list under them scrolls;
  the home section shows "Wird gerade angezeigt" and a remove action, the favourites section
  shows reorder arrows, promote-to-home and remove, and the empty-favourites hint renders
  when there are none.
- Suggestions overlay: searching `berlin` showed eight results (name plus admin region and
  country, e.g. "Berlin / Land Berlin, Deutschland" vs "Berlin / New Hampshire, Vereinigte
  Staaten"), each with a "+" action indicator, drawn in a floating panel directly under the
  field and over the favourites area; the panel is capped (about 45 % of the viewport, never
  more than the space between the field and the keyboard), cuts the last row off cleanly and
  scrolls on its own, and the favourites behind it did not move.
- Keyboard: the field stays fully visible and editable while the keyboard is open; the
  floating back button is hidden then and reappears as soon as the keyboard closes.
- Back order verified with `uiautomator` dumps after each key press: the first press closed
  the keyboard, the second closed the suggestions (the manager and its title stayed), the
  third returned to the dashboard — no loop, no accidental exit.
- Choosing a suggestion (Amsterdam) added it as favourite, cleared the query, closed the
  dropdown and stayed on the location screen; selecting the already saved Berlin again showed
  "Dieser Ort ist bereits gespeichert." and added nothing. **Found during this verification:**
  with the keyboard open that message was rendered behind it; the snackbar host now uses
  `imePadding()` so the message appears above the keyboard (re-verified by screenshot).
- Nine favourites plus the home entry: the list scrolls independently (the home section
  scrolls out while the field stays put), the first "up" and last "down" arrows are disabled,
  and after scrolling to the end the last favourite (Prag, bottom edge y = 2038 px) ends
  clearly above the floating back button (top edge y = 2179 px) and stays tappable.
- The floating button returns to the dashboard; tapping a favourite (Tokio) selects it and
  closes the manager — the dashboard then read "Montag, 23. November 2026, 04:00" while the
  device showed 20:06, with the strip starting at "Heute / 04:00" under the day label
  "Mo., 23. November": the corrected start interval and the location-local day labels still
  behave for a zone five hours ahead.
- Rolling window on the device at 20:05 local: the first chip is "Heute 20:00" followed by
  21:00, 22:00, 23:00 — the window begins in the interval that contains the current time.
- Switching the system theme (dark → light) closes the manager without losing any saved
  location, because the screen flag lives in the dashboard composable while the data lives in
  the ViewModel/DataStore.
- Logcat after all of the above: no `FATAL EXCEPTION`, no application errors (only an
  informational Compose hidden-API note).

Not verified manually: reordering across more than two favourites, the suggestion dropdown on
very small or landscape screens, TalkBack/screen-reader behaviour of the new floating button
and dropdown, low-API-level devices, physical devices and tablets.

## Known limitations

- The app has no coarse/fine location permission: the home location is chosen by search
  (which is the configurable behaviour required by the product spec).
- Offline support depends on previously fetched data; a first start without network shows an
  empty/error state instead of fabricated data.
- Hourly detail is available for the hours inside the rolling 24-hour window (which can reach
  into tomorrow), not for arbitrary days; the 16-day outlook is daily data only, because the
  compact day list has no hourly drill-down.
- The rolling hourly window is bounded by **24 hours of forecast time**, not by 24 records: a
  gap in the payload means fewer than 24 chips, and the horizon is never stretched just to
  fill the strip. A payload whose hours have all already elapsed yields no window at all, so
  the dashboard shows "Für den aktuellen Zeitraum sind keine Stundendaten verfügbar." instead
  of outdated hours. Scrolling further ahead than one day is not possible.
- The location manager is a full screen overlay inside `DashboardScreen` rather than a
  navigation destination, so its open/closed flag and the list's scroll position are not
  restored after a configuration change (theme switch or rotation closes it). The saved
  locations, the selection and the pending search are held by the ViewModel and survive.
- The suggestion dropdown is a custom anchored `Surface` instead of `ExposedDropdownMenu`,
  which is what allows the fixed height cap, the independent scrolling and the keyboard-aware
  placement; the trade-off is that it is not announced as a menu by accessibility services.
- A local day change is refreshed at most once per day by the hourly tick; if that single
  attempt fails, the app keeps showing the previous day's data until the next foreground
  resume, pull-to-refresh or refresh button.
- Verified on the API 37 phone emulator only; no physical device, tablet, or low-API-level
  device testing was done.
- Lint reports only "newer version available" advisories for Gradle 9.6.0, core-ktx,
  Compose BOM, the Kotlin plugins and kotlinx-serialization. Upgrading the Kotlin plugins
  beyond 2.2.x is intentionally avoided while AGP's embedded Kotlin stdlib is pinned.
