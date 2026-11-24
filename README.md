# Weather App (Wetter)

Native Android weather application written in Kotlin and Jetpack Compose. It shows live
weather data from [Open-Meteo](https://open-meteo.com/) for a configurable home location and
any number of favourite locations, caches responses locally, and works offline as long as a
cached forecast exists.

All user-visible text is German; source code, comments and this documentation are English.

## Feature summary

| Area | Behaviour |
| --- | --- |
| Current weather | Temperature, condition, feels-like, today's min/max, humidity, precipitation, cloud cover, wind speed/direction/gusts, pressure. Sustained wind and gusts are checked against the wind warning thresholds and, when exceeded, marked on the weather symbol (see "Wind and gust indicators"). |
| Hourly (rolling) | Horizontal strip with a rolling 24-hour window: it starts at the hourly interval that **contains** the current time, highlights it, labels every day the window touches ("Heute", "Morgen", weekday) and moves on its own when the hour changes. If that current hour is missing from the response, the earliest later hour starts the window; an already elapsed hour is never used as the start, and if the payload only contains elapsed hours the strip shows the unavailable-data note instead of outdated values. The window covers the current interval plus the following 23 hourly intervals by absolute timestamp, so a gap shortens the list rather than stretching the horizon. Every chip has the same outer height and the same internal layout whether or not it carries a day label, and the precipitation probability is rendered in the same size as the clock time so that "100 %" is never cut off. Tapping an hour opens a "Stundendetails" bottom sheet with all values for that hour, including a plain-text wind warning when that hour exceeds a threshold. |
| Days 1–2 | Two detailed cards ("Morgen", "Übermorgen") with condition, precipitation probability/amount, wind, gusts, sunshine and day length. |
| Days 3–15 | Compact list ("Ausblick") with weekday, date, condition icon, precipitation probability/amount and the min/max temperatures as numbers; up to the 16-day forecast horizon of the API. The written condition label and the temperature range bar were removed from these rows: the icon, the numbers and the wind marker carry the same information in less space, and the full wording stays in the detailed cards and in the accessibility label of the icon. |
| Locations | A home location plus favourites, managed on a dedicated full screen instead of a bottom sheet: the title and the search field stay fixed at the top, the saved locations live in a list that scrolls on its own, and search results appear in a floating dropdown directly under the search field, overlaying the list without moving it. A circular floating button in the bottom-left corner (above the navigation bar, hidden while the keyboard is open) returns to the dashboard; the system back button first closes the suggestions. Search by name (Open-Meteo geocoding), add as favourite, remove, reorder with up/down arrows, promote a favourite to home, and tap any entry to display it. Duplicate places are rejected (same id or same rounded coordinates) and reported without leaving the screen. Home is a single slot: promoting a favourite to home swaps the two entries, the previous home moving into the favourites at exactly the position the new home had, and the new home becomes the displayed location; no saved place is ever dropped and none appears twice. The last displayed location is restored on the next start. |
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

## Weather presentation

Two small, testable classifiers in `domain/model` decide *how* a condition is presented. They
never change what the API reported, only how it is drawn and worded.

### Rain and snow: exactly three intensities

`PrecipitationClassifier.classify(condition, precipitationMillimetres, snowfallCentimetres)`
returns a `PrecipitationStyle(family, intensity)`. Rain and snow each get exactly three levels,
and the icon and the short label are derived from the same value, so they can never disagree:

| Level | Rain label | Snow label | Symbol |
| --- | --- | --- | --- |
| `LIGHT` | Leichter Regen | Leichter Schneefall | cloud + 1 drop / 1 flake |
| `MODERATE` | Regen | Schneefall | cloud + 2 drops / 2 flakes |
| `HEAVY` | Starker Regen | Starker Schneefall | cloud + 3 drops / 3 flakes |

- The **WMO code is the base** of the decision: 61/80 → light, 63/81 → moderate, 65/82 → heavy
  rain; 71/85 → light, 73 → moderate, 75/86 → heavy snow (the Open-Meteo wording of those
  codes).
- A reported **amount may refine that band by at most one step** — never more, because the code
  and the amount are two different statements about the same hour. The thresholds are
  `MODERATE_RAIN_MILLIMETRES = 0.5`, `HEAVY_RAIN_MILLIMETRES = 2.5` (mm) and
  `MODERATE_SNOW_CENTIMETRES = 0.5`, `HEAVY_SNOW_CENTIMETRES = 1.5` (cm liquid-equivalent
  snowfall, the unit Open-Meteo reports `snowfall` in). A missing, zero, negative or non-finite
  amount carries no information and leaves the code band untouched.
- **Precipitation probability is never used** for the intensity: it says how likely precipitation
  is, not how heavy it would be. Daily precipitation sums are deliberately not used for the
  compact day rows either — a 24-hour total says nothing about a single hour.
- Conditions that are not plain rain or snow keep their own family and therefore their own
  symbol and their precise wording: drizzle (51/53/55), freezing rain and freezing drizzle
  (56/57/66/67), snow grains (77), thunderstorm (95) and thunderstorm with hail (96/99). They
  carry no intensity, so they are never reduced to a drop or flake count.
- `shortDescriptionRes()` is used in the compact places (hero card, hourly chip, detailed day
  card) and returns the three-level wording; `descriptionRes()` keeps the precise meteorological
  label ("Kräftige Regenschauer", "Gefrierender Regen", …) for the hourly detail sheet and for
  the accessibility label of the icon.

### Wind and gust indicators

`WindWarningClassifier.classify(sustainedWind, gusts)` judges the two measurements **separately**
and reports which one caused the warning, so a gust is never presented as sustained wind.

| Measurement | `STRONG` ("Starker Wind") | `STORM` ("Sturm") |
| --- | --- | --- |
| Sustained wind | ≥ 39 km/h | ≥ 75 km/h |
| Gusts | ≥ 50 km/h | ≥ 85 km/h |

- The sustained thresholds are the lower bounds of Beaufort 6 and Beaufort 9; the gust
  thresholds sit above the sustained ones because a short gust is a weaker statement than
  sustained wind of the same speed. They are documented constants, not tuned per screen.
- The **higher level wins**. When both measurements reach the same level, the warning is
  attributed to the sustained wind; the gust cause ("Starke Windböen") is only reported when the
  gusts push the level strictly higher.
- Missing, negative or non-finite values never produce a warning.
- The indicator is **not colour-only**: it is a separate wind glyph with its own accessibility
  label ("Starker Wind", "Starke Windböen", "Sturm"), and the hourly detail sheet states the
  cause and both speeds in text ("Sturmwarnung: Böen bis 92 km/h bei anhaltendem Wind 45 km/h.").
  Storm is drawn in a theme-specific red (`StormRedLight` / `StormRedDark`, both ≥ 3:1 against
  the shipped background), strong wind stays neutral. The global Material `error` colour is
  deliberately untouched, because it also carries the destructive location action.

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
.\gradlew.bat testDebugUnitTest      # unit tests (195)
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat connectedDebugAndroidTest   # 33 instrumented + Compose UI tests on a running device
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
  selection fallbacks, search debouncing/short queries and **home switching**
  (`LocationRepositoryTest`, 23 tests: the previous home takes the favourite slot the new home
  leaves, the New York City/Hamburg/Dresden/Paris/Madrid sequence keeps all five places, repeated
  home changes, six concurrent taps, duplicates already present in stored state, a single
  favourite, a missing home entry, the new home becoming the selected location, the selection
  surviving a restart, and no write at all when the current home is assigned again);
  **row action colours** (`LocationRowActionColorsTest`, 5 tests: the enabled move, promote and
  remove tints reach at least 3:1 contrast against the shipped light and dark backgrounds, the
  disabled tint is clearly weaker yet still visible, and the three emphasis levels are
  `onSurfaceVariant`, `primary` and `error`); `MainViewModel` state transitions
  (initial load, location switch, late responses, hourly tick without extra request, silent
  refresh after a local day change, foreground refresh, cached-while-refreshing, error states);
  **precipitation intensity** (`PrecipitationStyleTest`, 12 tests: the WMO codes behind the three
  rain and three snow levels, showers sharing those levels with steady rain, drizzle, freezing
  precipitation, snow grains, thunderstorm and hail keeping their own family and no intensity,
  cloudiness and fog not being precipitation at all, a missing amount keeping the code band,
  zero/negative/NaN/infinity carrying no information, an amount refining a code by **at most one
  step** in both directions, the documented mm and cm thresholds, a snow code staying snow when
  only millimetres are known, and no amount ever inventing a family without a condition);
  **wind warnings** (`WindWarningClassifierTest`, 10 tests: no indicator below the thresholds,
  the 39/75 km/h sustained thresholds being inclusive including 38.9 and 74.9, strong gusts
  warning while the sustained wind stays below its own threshold, storm gusts attributed to the
  gusts, the stronger measurement deciding the level with equal levels attributed to the sustained
  wind, gust thresholds lying above the sustained ones, and missing, negative, NaN and infinite
  values never warning); and the **presentation mapping** (`WeatherPresentationTest`, 13 tests:
  three distinct drawables for the three rain and the three snow levels, showers using the symbol
  of their intensity, a refined intensity changing symbol *and* label together,
  drizzle/sleet/snow grains/thunderstorm/hail keeping their own symbols, day and night sky
  symbols, the short label matching the symbol, the detailed description staying meteorologically
  precise, wind labels naming level and cause, storm being red while strong wind stays neutral in
  both palettes, both wind colours reaching 3:1 contrast against the shipped light and dark
  backgrounds, and no warning having no colour).
- **Instrumented tests** (`app/src/androidTest`): Room cache store round-trip, replace,
  delete (in-memory database); Compose UI tests for the rendered dashboard, the hourly strip
  starting at the current hour with its day label, the hourly detail sheet, the "no usable
  observation time" label, the error state with its retry action, the "Gespeicherte Daten"
  label for cached data, and the toolbar opening the **full screen location manager**
  (`LocationManagerScreenTest`, 13 tests): the manager shows its own title, the fixed search
  field and the saved places; the field and title stay in place while a 25 favourite list is
  scrolled and the home entry scrolls out with the list only; the last favourite can be
  scrolled clear of the floating back button and the button keeps at least a 48 dp touch
  target (bounds compared, no fixed pixel values); suggestions overlay the favourites without
  moving them; choosing a suggestion adds the favourite, clears the query and closes the
  dropdown while staying on the location screen; the same place cannot be added twice;
  duplicate feedback is shown on the manager itself; back closes the suggestions first and the
  screen only afterwards; the floating button returns to the dashboard; an empty favourite
  list still keeps the search field usable; the first "up" and last "down" arrows report
  `Disabled` through semantics while the middle rows do not; a long place name never overlaps an
  action button and no button is cut off at the screen edge; and driving a **real
  `LocationRepository`** (with an in-memory `SettingsStore`) through the UI promotes a favourite
  to home, puts the previous home back into the favourites at the slot the new home left, keeps
  every place on screen exactly once and stores the new home as the selected location.
- **Presentation layout tests** (`WeatherPresentationUiTest`, 9 tests, added in this iteration):
  every detail label of the hero card is displayed and stays inside the card bounds, and the
  first detail column never reaches into the second one; three hourly chips that cross midnight
  (so one carries "Heute", one no label and one "Morgen") have **identical heights, identical top
  edges and identical icon top edges**, compared against each other rather than against fixed
  pixel values; a "100 %" probability is shown and its text bounds stay inside the chip bounds;
  a compact outlook row shows probability plus amount, both temperatures as numbers, the weekday
  and the short date, and **no written condition label**; the day's wind warning appears as an
  accessibility label on the row icon; the hero card shows no indicator for ordinary wind,
  "Starker Wind" at exactly 39 km/h, "Starke Windböen" for 55 km/h gusts with 20 km/h sustained
  wind, and "Sturm" at 80 km/h — checked by recomposing the same card, not by colour; exactly
  one of 24 hourly chips carries the storm indicator when only that hour exceeds the threshold;
  the new symbols and their indicators render in the light **and** the dark theme; and the
  shipped rain and snow vectors are parsed with `XmlPullParser` to prove they draw **one, two
  and three** precipitation marks on the same cloud path.

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

### Location management screen and hourly start

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

### Home location switching, icon contrast and narrow rows (current iteration)

Same emulator, real API traffic, screenshots `app/build/verify/h01…h09*.png`:

- Documented switch sequence: home New York City with favourites Hamburg, Dresden, Paris, Madrid.
  Promoting **Hamburg** left the favourites as New York City, Dresden, Paris, Madrid — New York
  City took exactly the slot Hamburg had occupied, and the home section read "Hamburg / Freie und
  Hansestadt Hamburg, Deutschland / Wird gerade angezeigt", so the new home is also the location
  that is displayed. Promoting **Paris** afterwards produced home Paris with favourites
  "New York City, Dresden, Hamburg, Madrid", exactly the documented expectation.
- Force-stop and restart: the dashboard opened on Paris (selection persisted), and the manager
  still listed home Paris plus New York City, Dresden, Hamburg, Madrid in that order.
- Single favourite: home New York City with only Hamburg saved; promoting Hamburg produced home
  Hamburg and the favourite list "New York City" — nothing lost, nothing doubled.
- No duplicate weather request when promoting a home: the dashboard header changed to the new
  home in the same frame, and Logcat stayed free of application warnings or errors (the app
  process logged nothing but a single informational line during the whole scenario).
- Icons in **light and dark** theme: the reorder arrows read as `onSurfaceVariant`, the
  promote-to-home star as `primary` and the remove action as `error` on both backgrounds; the
  disabled first "up" and last "down" arrows are clearly fainter than their neighbours yet stay
  visible, so the disabled state does not depend on colour alone (the semantics also report it).
- Narrow layout: with `wm size 720x1280` and `wm density 360` (320 dp wide) at `font_scale 1.3`,
  a favourite row gives its full width to the name and region — "New York City" and
  "New York, Vereinigte Staaten" render completely instead of collapsing into a few ellipsised
  characters — and the four actions move to their own right aligned line below the text, still
  48 dp each and inside the window. Normal phone widths (411 dp) keep the previous single-line
  layout unchanged. Screenshots `h06_narrow_fontscaled.png` (before) and
  `h07_narrow_stacked.png` (after) show the difference.
- The full screen manager itself is untouched: fixed title and search field, independently
  scrolling list, floating suggestion dropdown, back button at the bottom left and the bottom
  padding that keeps the last favourite clear of it all still behave as before, re-checked by the
  existing instrumented tests.

### Readability, precipitation intensity and wind indicators (current iteration)

Same emulator, real API traffic, screenshots `app/build/verify/v01…v15*.png`. The home location
was set to **Ushuaia**, which the API currently reports with light drizzle, 30 km/h sustained
wind and 87 km/h gusts — a case that exercises the gust rule and the storm colour at the same
time.

- **Hero card labels are complete.** "Luftfeuchtigkeit" renders in full with a clear left margin.
  Measured on the screenshot: the text node occupies x = 95…311 px and the drawn ink spans
  x = 97…308 px, i.e. the first stroke of the "L" starts inside its own layout box and the card
  edge is at x = 47 px — nothing is cut. The same holds for Niederschlag, Wind, Windböen,
  Bewölkung and Luftdruck.
- **Sustained wind and gusts stay separate.** The card shows "Wind 30 km/h WSW" and
  "Windböen 87 km/h" as two values; the hourly detail sheet for 14:00 reads
  "Wind 30 km/h", "Windrichtung WSW (256°)", "Windböen 85 km/h" and the note
  *"Warnung vor starken Windböen: Böen bis 85 km/h bei anhaltendem Wind 30 km/h."* — the gust is
  named as a gust, never presented as sustained wind.
- **The indicator is not colour-only, and the level is visible.** Pixel sampling of the glyph
  inside the wind badge gives, in the dark theme: hero badge (gusts 87 → storm) `#EF5350`,
  hourly chip 14:00 (gusts 84.x → strong) `RGB(180,194,207)` neutral, hourly chip 15:00 (storm)
  `#EF5350`, "Morgen" day card (strong) neutral again. In the light theme the storm badge is
  `#C62828` and the strong badge `RGB(70,85,99)`. The accessibility labels reported by
  `uiautomator` are "Sturm" for the storm hours and "Starke Windböen" for the gust-driven
  strong-wind hours, so the difference is also available without colour.
- **Hourly chips are uniform.** With "Heute" on the first chip and no label on the others, the
  four visible chips have the same outer bounds (y = 1515…1883 px for every clickable chip) and
  their clock times, symbols, temperatures and percentages sit on the same lines. The same is
  asserted numerically by the instrumented test.
- **"100 %" fits.** Every chip of this stormy day shows "100 %" completely, at the same size as
  the clock time.
- **Compact outlook rows carry numbers, not prose.** The "Ausblick" list shows
  "Fr. / 27.11. / icon / 88 % · 1,4 mm / 5° / 14°" and so on for all 11 remaining days: no
  written condition label and no temperature range bar anywhere in the list, min muted and max
  emphasised, wind badges on the windy days. The 07.12. row shows a **single** drop next to
  "66 % · 20 mm", which is the documented behaviour: the daily sum is deliberately not used to
  grade an icon, the day's WMO code is.
- **Drizzle keeps its own symbol and its own words**: "Leichter Nieselregen" with the three-dot
  drizzle drawable, never a raindrop count.
- **Light and dark theme**: both captured for the hero card, the hourly strip and the outlook
  list; the layout is pixel-identical (same node bounds) and only the palette changes.
- Logcat during all of the above: empty crash buffer, no `FATAL EXCEPTION`, no application
  warnings or errors (only emulator-side `PackageConfigPersister`, `InteractionJankMonitor` and
  an "Unexpected CPU variant for x86" notice from the runtime).

Not verified manually: reordering across more than two favourites, the suggestion dropdown in
landscape, TalkBack/screen-reader behaviour of the floating button, the dropdown and the row
actions, very large font scales (≥ 2.0) together with the widest action set, low-API-level
devices, physical devices and tablets.

Not verified manually for the weather presentation either: a live **snow** day (the three snow
symbols and their labels are covered by unit tests, by the instrumented rendering test and by the
vector-content test, but no snowing location was opened on the device), a thunderstorm or hail
location, font scales ≥ 1.5 on the hourly strip and the outlook rows, and the wind badge under
TalkBack (the labels exist in the semantics tree, but no screen reader was run).

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
- The precipitation and wind thresholds are **documented constants** (`PrecipitationClassifier`
  and `WindWarningClassifier`), not user settings. They are deliberately conservative: a reported
  amount may move a WMO code band by **at most one** intensity step, so an extreme mismatch
  between the code and the amount is intentionally not fully corrected, and precipitation
  probability never influences the intensity.
- The compact outlook rows grade their symbol from the **day's WMO code only**. A 24-hour
  precipitation sum never changes an icon or a label, because a daily total says nothing about a
  single hour — which is why a day can legitimately show one drop next to "66 % · 20 mm".
- The wind badge is a static overlay in the corner of the weather symbol. It marks *that* a
  threshold was exceeded and which measurement caused it; it is not an official weather warning,
  has no duration or area, and is not de-duplicated against the DWD or Met Office warning
  services.
- The weather symbol itself keeps the generic accessibility label "Wettersymbol" and the
  condition wording is separate text. Because that text was removed from the compact outlook
  rows, a screen reader there announces the weekday, the date, the precipitation numbers, both
  temperatures and the wind warning label, but not the condition. The detailed day cards and the
  hourly detail sheet still state it in words.
- Verified on the API 37 phone emulator only; no physical device, tablet, or low-API-level
  device testing was done.
- Lint reports only "newer version available" advisories for Gradle 9.6.0, core-ktx,
  Compose BOM, the Kotlin plugins and kotlinx-serialization. Upgrading the Kotlin plugins
  beyond 2.2.x is intentionally avoided while AGP's embedded Kotlin stdlib is pinned.
