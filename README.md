# WetterPlus

Native Android weather application written in Kotlin and Jetpack Compose. It shows live
weather data from [Open-Meteo](https://open-meteo.com/) for a configurable home location and
any number of favourite locations, caches responses locally, and works offline as long as a
cached forecast exists.

The application is named **WetterPlus** everywhere the user sees it — launcher label, system
application info and the dashboard toolbar — while the application ID and package name stay
`com.example.weatherapp`, so an installed build updates in place instead of installing beside it.

All user-visible text is German; source code, comments and this documentation are English.

## Feature summary

| Area | Behaviour |
| --- | --- |
| Current weather | Temperature, condition, feels-like, today's min/max, humidity, precipitation, cloud cover, wind speed/direction/gusts, pressure. Sustained wind and gusts are checked against the wind warning thresholds and, when exceeded, marked on the weather symbol (see "Wind and gust indicators"). |
| Hourly (rolling) | Horizontal strip with a rolling 24-hour window: it starts at the hourly interval that **contains** the current time, highlights it, labels every day the window touches ("Heute", "Morgen", weekday) and moves on its own when the hour changes. If that current hour is missing from the response, the earliest later hour starts the window; an already elapsed hour is never used as the start, and if the payload only contains elapsed hours the strip shows the unavailable-data note instead of outdated values. The window covers the current interval plus the following 23 hourly intervals by absolute timestamp, so a gap shortens the list rather than stretching the horizon. Every chip has the same outer height and the same internal layout whether or not it carries a day label, and the precipitation probability is rendered in the same size as the clock time so that "100 %" is never cut off. Tapping an hour opens a "Stundendetails" bottom sheet with all values for that hour, including a plain-text wind warning when that hour exceeds a threshold. |
| Days 1–2 | Two detailed cards ("Morgen", "Übermorgen") with condition, precipitation probability/amount, wind, gusts, sunshine and day length. |
| Days 3–15 | Compact list ("Ausblick") with weekday, date, condition icon, precipitation probability/amount and the min/max temperatures as numbers; up to the 16-day forecast horizon of the API. The written condition label and the temperature range bar were removed from these rows: the icon, the numbers and the wind marker carry the same information in less space, and the full wording stays in the detailed cards and in the accessibility label of the icon. |
| Locations | A home location plus favourites, managed on a dedicated full screen instead of a bottom sheet: the title and the search field stay fixed at the top, the saved locations live in a list that scrolls on its own, and search results appear in a floating dropdown directly under the search field, overlaying the list without moving it. The screen is entered and left through the **back arrow in its header** ("← Orte verwalten"), like every other secondary screen; the system back button first closes the suggestions. Search by name (Open-Meteo geocoding), add as favourite, remove, reorder with up/down arrows, promote a favourite to home, and tap any entry to display it. Duplicate places are rejected (same id or same rounded coordinates) and reported without leaving the screen. Home is a single slot: promoting a favourite to home swaps the two entries, the previous home moving into the favourites at exactly the position the new home had, and the new home becomes the displayed location; no saved place is ever dropped and none appears twice. The last displayed location is restored on the next start. |
| Refresh | Pull-to-refresh, toolbar refresh button (both bypass the cache), automatic refresh when the app returns to the foreground, and a silent background refresh when the cached response belongs to a previous calendar day in the location's time zone. The hourly window itself advances every hour without any network request. |
| Offline | The last response per location is cached; with no network the cached data is shown instead of an empty screen. |
| Appearance | Material 3, rounded cards, condition-dependent gradient header, custom vector weather icons, German date/number formatting, 24-hour time. The theme is a user choice on its own screen: **Systemeinstellung / Hell / Dunkel**, applied immediately, stored, and restored on the next start (see [Appearance and theme](#appearance-and-theme)). |
| Navigation | One hierarchy for every secondary screen: dashboard → location management, dashboard → settings → appearance, dashboard → settings → information → licence detail. Each secondary screen carries a back arrow in its header, the system back button follows the same path, and the dashboard keeps its state (scroll position, loaded forecast) while a secondary screen is open. |
| Settings and information | A settings screen with two entries ("Darstellung", "Infos"), the appearance page described above, and an information page with the installed version and build number, the weather services behind the data with their links, and the licences of the data and of the shipped libraries — readable offline, with the full licence text opening in the browser. |
| Launcher icon | A custom adaptive icon (warm sun with a soft cloud over it on a deep blue field) with a monochrome layer for themed icons; no template artwork and no bitmaps, so it renders from the same vector on every density. |

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
- The glyph is **part of the icon footprint**: `WeatherIcon` draws it in the free upper end corner
  of the same box, at 34 % of the symbol size (never below 9 dp), without a backing disc and
  without changing the layout. Every weather drawable keeps that corner empty — the sun rays are
  rotated by 22.5°, the moon opens to the lower left, and the sky of a partly cloudy symbol sits
  in the upper *start* corner — so the indicator never covers the sun, the moon, a cloud or a
  precipitation mark.
- On the coloured header gradient of the hero card the theme neutrals would be invisible, so that
  one place passes `HeaderWindIndicatorColors` (`HeaderWindStrong` neutral, `HeaderStormRed`
  storm) instead of the theme pair. The limit of that choice is documented under
  [Known limitations](#known-limitations).

### Weather icon design system

All 19 weather symbols are Android `VectorDrawable`s in `app/src/main/res/drawable/`, named
`weather_<state>.xml`: `clear_day`, `clear_night`, `partly_cloudy_day`, `partly_cloudy_night`,
`cloudy` (the fallback for unknown data), `overcast`, `rain_light`, `rain_moderate`,
`rain_heavy`, `drizzle`, `snow_light`, `snow_moderate`, `snow_heavy`, `snow_grains`, `sleet`,
`fog`, `thunderstorm`, `hail`, plus `wind` for the indicator. Consistency is the design goal:
one grid, one cloud, one drop, one flake, one bolt, one fog bar set, one palette.

**Geometry.** Every icon uses `android:width="48dp"`, `android:height="48dp"` and a
`48 × 48` viewport with a four-unit safe margin. The elements are placed on fixed bands, so a
cloud, its precipitation and the wind corner never collide:

| Element | Construction |
| --- | --- |
| Cloud | One `pathData` (three circles plus the flat base, bounds x 6…38, y 8.5…31). The shadow is the *same* path inside `<group android:translateY="2">`, drawn first; overcast adds the same path again at scale 0.62 behind. |
| Sun | Disc r 8.5 at (22, 24) plus an accent disc r 8.5 at (22, 25.5); eight round-capped rays from r 10.5 to r 12.2 at 22.5° + 45°·k. The offset keeps the rays out of the reserved wind corner. |
| Moon | One crescent: r 11.5 at (22, 24) cut by r 11 at (28.5, 18.5), opening to the lower left. |
| Drop | One teardrop, 6.6 × 9.6 units, centred on y 39.2. One mark → x 22; two → 16.5 / 27.5; three → 11 / 22 / 33. |
| Flake | Three stroked lines through one centre (vertical plus ±60°), 7.8 units across, same centres as the drops. |
| Bolt | One closed zigzag under the cloud; hail shifts it three units to the left to make room for the hail dots. |
| Fog | Three horizontal stroked bars (y 32.5 / 37.5 / 42.5), the middle one offset to the right. |
| Drizzle, snow grains, hail dots | Circles r 2.2–2.5 on the same precipitation band. |
| Wind | Three stroked curved lines with round caps, tinted in code, never baked into a weather icon. |

Precipitation always occupies the band y 34…44, the cloud y 8.5…33 and the sky symbol is centred
on (22, 24). The rectangle x 32.6…45, y 1.4…14 is **reserved for the wind indicator** and stays
empty in every symbol.

**Palette.** Icons never hard-code a colour; they reference tokens from `res/values/colors.xml`,
and `res/values-night/colors.xml` overrides the same names. The theme therefore changes the
colours of the *identical* geometry:

| Token | Light | Night | Used for |
| --- | --- | --- | --- |
| `weather_sun` | `#F5BE45` | `#FFD166` | sun disc |
| `weather_sun_accent` | `#F2A93B` | `#FFB347` | sun shade |
| `weather_cloud` | `#93AEC7` | `#A8BED1` | cloud silhouette |
| `weather_cloud_shadow` | `#6E8FAE` | `#7898B5` | offset cloud shadow |
| `weather_cloud_secondary` | `#AFC5D8` | `#C3D5E4` | second cloud of `overcast` |
| `weather_rain` | `#2A7BC8` | `#4FA8EC` | drops, freezing precipitation |
| `weather_snow` | `#63B8E8` | `#D5F0FF` | flakes, snow grains |
| `weather_hail` | `#5AA8D8` | `#CFE9FA` | hail dots |
| `weather_moon` | `#F2C14E` | `#FFE3A3` | crescent |
| `weather_lightning` | `#F5B429` | `#FFD54F` | bolt |
| `weather_fog` | `#7C93A8` | `#91A6B8` | fog bars |

Rules for changing the family: never scale or move a single element of one icon — change the
shared path in every icon that uses it; add a new state by reusing the existing cloud, drop,
flake, bolt and dot paths; change colours only in the two `colors.xml` files. The instrumented
`WeatherPresentationUiTest` enforces the shared viewport, the shared cloud silhouette and the
identical drop and flake geometry, and a palette test checks every token against the shipped
light and dark surfaces.

**Sizes.** The application renders the symbols at 30 dp (hourly chip, compact day row), 44 dp
(day card), 46 dp (hourly detail sheet) and 76 dp (hero card). The gallery below shows 24, 32,
48 and 72 dp, which brackets those sizes.

**Previewing the whole family.** `app/src/debug/java/com/example/weatherapp/ui/gallery/` holds a
development-only screen that renders every condition — one row per `WeatherCondition`, plus rows
that combine a symbol with each wind level — at 24, 32, 48 and 72 dp on four backgrounds: the
light card, the dark card and two header gradients. It lives in the debug source set, is
declared only in `app/src/debug/AndroidManifest.xml` with `android:exported="false"`, and is not
reachable from the production navigation.

- In Android Studio: open `WeatherIconGallery.kt` and use the two `@Preview` functions
  (light and dark).
- On a device or emulator, start it in process (it is not exported) with the debug-only helper
  test, which writes one PNG per page into the app's files directory:

  ```powershell
  .\gradlew.bat assembleDebugAndroidTest
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
  adb shell am instrument -w -e class com.example.weatherapp.ui.gallery.WeatherIconGalleryScreenshotTest `
      com.example.weatherapp.test/androidx.test.runner.AndroidJUnitRunner
  adb exec-out run-as com.example.weatherapp tar -c -C files/icon_gallery . > gallery.tar
  ```

  `adb shell cmd uimode night yes|no` switches the theme of the run. The screenshots are a
  review artefact of the debug build and are not part of the repository.


## Branding and launcher icon

The name the user sees is `R.string.app_name` = **WetterPlus**. The same resource backs the
launcher label and therefore the system application info; the dashboard toolbar shows it as a
permanent title. The application ID (`com.example.weatherapp`) and the package directories are
deliberately unchanged, so installing a build over an existing one updates it in place.

The launcher icon is a **adaptive icon** in `res/mipmap-anydpi/ic_launcher.xml` and
`ic_launcher_round.xml`, built from three vectors and no bitmaps (minSdk 33 always takes the
adaptive path, so the density-specific template PNGs were removed):

| Layer | File | Content |
| --- | --- | --- |
| Background | `drawable/ic_launcher_background.xml` | flat `#17456A`, the dark blue of the light theme's primary container |
| Foreground | `drawable/ic_launcher_foreground.xml` | sun disc `#F5BE45` with an accent disc `#F2A93B` one step lower, eight round-capped rays, and the cloud family's silhouette `#93AEC7` with its offset shadow `#6E8FAE` in front of the lower right of the sun |
| Monochrome | `drawable/ic_launcher_monochrome.xml` | the same sun and cloud as a single-colour silhouette for themed icons |

The artwork reuses the palette and the cloud path of the [weather icon family](#weather-icon-design-system),
scaled by a `<group>` transform onto the 108 × 108 dp adaptive canvas; everything stays inside the
66 × 66 dp safe zone (x/y 21…87), so no mask cuts a ray or the cloud. There is no text, no
gradient and no shadow blur, and the in-app weather symbols are untouched by this layer — the
launcher icon and the condition icons are separate assets by design.

## Architecture

MVVM with unidirectional state flow. A small manual container (`di/AppContainer`, owned by
`WeatherApplication`) provides the singletons; no DI framework is used.

```
Composable screens (WeatherRoot + AppBackStack: DashboardScreen, LocationManagerScreen,
                     SettingsScreen, AppearanceScreen, AboutScreen, LicenceDetailScreen,
                     LocationSetupScreen)
        ▲  MainUiState (StateFlow)                      ▼ user events
   ui/MainViewModel  ── combines settings + weather flows, owns refresh scheduling
   ui/settings/AppearanceViewModel ── theme mode state, applies and stores the choice
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
  colours because the M3 elevation tint would otherwise tint the surfaces. Which palette is used
  is a user preference (`ThemeMode`), not only the system setting — see
  [Appearance and theme](#appearance-and-theme).
- **Navigation without a navigation library.** `ui/navigation/AppBackStack` is a small immutable
  list of destinations held in `rememberSaveable` with its own `Saver`; a single `BackHandler`
  feeds the system back button into it. That is enough for a five-screen hierarchy, avoids a new
  dependency, and keeps the whole path restorable after rotation or a theme switch.
- **AGP 9.5.2 with built-in Kotlin.** The Kotlin plugin is not applied; the embedded Kotlin
  2.2.10 stdlib is force-pinned in `build.gradle.kts`, and dependency versions must stay
  compatible with it.

## Navigation and screens

`WeatherRoot` owns the whole screen stack. One destination is on top at a time, and the
hierarchy is the same for the arrow in the header and for the system back gesture:

```
Dashboard (toolbar: WetterPlus · Aktualisieren · Orte · Einstellungen)
├── Orte verwalten
└── Einstellungen
    ├── Darstellung
    └── Infos
        └── Lizenzdetail (for one licence entry)
```

- Every secondary screen is built from `ui/components/SecondaryScreen.kt`: a header with a
  back arrow (`contentDescription` "Zurück") to the **start** of the title, then a scrolling
  content column. The arrow is a 48 dp `IconButton`, so the touch target is met without a
  floating button anywhere in the app.
- The dashboard stays composed underneath a secondary screen. Its scroll position, its loaded
  forecast and its hourly window are therefore intact when the user comes back, and no request
  is sent when opening or closing a settings page. While it is covered it is marked
  `hideFromAccessibility()` and its root swallows taps, so the hidden layer can neither be
  reached by TalkBack nor receive a touch that belongs to the screen on top.
- The location manager keeps its own inner back order: the first back press closes the search
  suggestions, the second leaves the screen.
- The toolbar title is the application name, not the location; the location stays visible in
  the current-weather card, which is the only place it is needed.

## Appearance and theme

`domain/model/ThemeMode` is `SYSTEM`, `LIGHT` or `DARK`; the default is `SYSTEM`. The choice is
stored in the same DataStore file as the locations, under the key `theme_mode`, by
`data/local/ThemeStore` (`DataStoreThemeStore`). A missing, damaged or unknown stored value
decodes to `SYSTEM` rather than failing.

- `ui/settings/AppearanceViewModel` is created by `AppContainer` while the application starts, so
  the stored mode is already in state before the first frame; `MainActivity` reads it for the
  very first composition, which avoids showing the system scheme for a moment before the pinned
  light or dark scheme takes over.
- Selecting a mode applies it immediately — no restart, no activity recreation — and writes it to
  disk. If the write fails, the mode stays applied for the session and the appearance screen says
  so instead of pretending it was saved.
- `MainActivity` also updates `isAppearanceLightStatusBars` / `isAppearanceNavigationBars`
  whenever the effective dark/light decision changes, so the system bars stay readable when the
  app pins a scheme that differs from the system setting.
- The theme changes nothing else: it never touches the saved locations and never reloads the
  weather (asserted by `AppearanceViewModelTest`).

## Versioning

`app/build.gradle.kts` carries the version: `versionName = "1.0.0"` in `MAJOR.MINOR.PATCH` form
and a positive `versionCode` that only ever increases (currently 2, because the first published
build of this repository was 1). The About screen does not repeat those numbers: it reads
`PackageInfo.versionName` and `longVersionCode` from the installed package, so the displayed
values cannot drift away from the build that is actually running.

## Build and run

Android Studio (or a JDK 17+ and an Android SDK) is required. If `java` is not on `PATH` and
there is no `local.properties`, export the toolchain first:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

```powershell
.\gradlew.bat assembleDebug          # debug APK
.\gradlew.bat testDebugUnitTest      # unit tests (229)
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat connectedDebugAndroidTest   # 74 instrumented + Compose UI tests on a running device
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
  values never warning); and the **presentation mapping** (`WeatherPresentationTest`, 16 tests:
  three distinct drawables for the three rain and the three snow levels, showers using the symbol
  of their intensity, a refined intensity changing symbol *and* label together,
  drizzle/sleet/snow grains/thunderstorm/hail keeping their own symbols, day and night sky
  symbols, the short label matching the symbol, the detailed description staying meteorologically
  precise, **every condition and intensity resolving to a drawable of the icon family** so no
  state can point at a missing resource, the day and night variant of one sky state being a
  different symbol, wind labels naming level and cause, storm being red while strong wind stays
  neutral in both palettes, both wind colours reaching 3:1 contrast against the shipped light and
  dark backgrounds, the **header wind palette staying readable on every header gradient** (neutral
  at 3:1 or better, storm red distinguishable, neutral lighter than the gradient and storm red
  actually red), and no warning having no colour).
- **Theme, navigation and information content** (31 tests):
  `ThemeModeTest` (8) — the default is `SYSTEM`, every stored name round-trips, an unknown, blank
  or malformed value falls back to `SYSTEM`, matching is trimmed and case-insensitive, the stored
  form is the enum name, `SYSTEM` follows the system while `LIGHT` and `DARK` ignore it, and the
  DataStore decoder is checked directly;
  `AppearanceViewModelTest` (7) — `SYSTEM` on a fresh store, a stored mode is restored on start,
  a selection is applied **before** the write finishes and then stored, every option is stored as
  itself, a failing write keeps the choice and reports it, the warning is cleared by the next
  selection, and a theme change never writes the location settings nor triggers a forecast request
  (both stores wired to a real `MainViewModel`);
  `AppBackStackTest` (10) — the dashboard is the root and offers no back action, dashboard ↔
  location management, dashboard ↔ settings, settings ↔ appearance, settings ↔ about, licence
  detail returns to about and not to settings, popping the dashboard is a no-op that can never
  empty the stack, the stack round-trips through its stored names, and unknown or truncated stored
  names come back as the dashboard instead of crashing;
  `AboutContentTest` (6) — licence ids are unique and resolvable, every entry carries resource
  backed text (nothing hard-coded), every link is an https URL with a host or deliberately empty,
  the forecast API, the geocoding API and the GeoNames database are named together with the data
  licences, the Apache component list mirrors the shipped artifacts and excludes protobuf, and the
  BSD-licensed protobuf runtime inside DataStore plus the project's own artwork are listed
  separately.
- **Instrumented tests** (`app/src/androidTest`): Room cache store round-trip, replace,
  delete (in-memory database); Compose UI tests for the rendered dashboard, the hourly strip
  starting at the current hour with its day label, the hourly detail sheet, the "no usable
  observation time" label, the error state with its retry action, cached data being presented
  under the "Zuletzt aktualisiert" line **without** any status strip below it, and the toolbar
  opening the **full screen location manager**
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
- **Navigation through the real root composable** (`WeatherRootNavigationTest`, 12 tests): the
  dashboard toolbar carries the application name; settings, location management, appearance, about
  and a licence detail each open as their own screen and return to the right parent through the
  header arrow; the licence detail returns to about and not to settings; the location manager has
  **exactly one** back arrow, so the floating button is gone; the **system back button** walks the
  same hierarchy (appearance → settings → dashboard, and licence → about → settings → dashboard);
  and opening settings, appearance and the location manager and coming back sends **no** forecast
  request, writes the location settings **no** extra time, and keeps the selected location and the
  favourites untouched.
- **Appearance page** (`AppearanceScreenTest`, 8 tests): the shared header with the back arrow, all
  three options offered, exactly the selected mode marked (and only one), `SYSTEM` marked when
  nothing was chosen, tapping a row reports exactly that mode, a failed write is reported on the
  page and no warning appears otherwise, and the stored mode — not the system setting — decides the
  colour scheme: a pinned light scheme equals a light system and differs from a pinned dark one.
- **Information page** (`AboutScreenTest`, 7 tests): the installed package really is labelled
  **WetterPlus** (the launcher label and the string resource agree), the version and the build
  number on screen are read from `PackageInfo` rather than written into the layout, the weather
  source section and the licence section are present, the model wording states that the model
  depends on the location and names `best_match`, every licence row is listed and opens its own
  page, and a licence page carries author, licence identifier, summary and the offline note without
  a network — the library page additionally lists the components it covers.
- **Theme preference against a real DataStore file** (`ThemeStoreTest`, 5 tests): `SYSTEM` for a
  fresh file, the chosen mode written to the preferences and read back by a store created
  afterwards, every mode stored as itself, a hand-written invalid value falling back to `SYSTEM`,
  and a theme write leaving the saved locations in the same file identical.
- **Toolbar and header touch targets**: the three dashboard actions and the header back arrow are
  measured in the semantics tree and must be at least 48 dp on both axes, aligned on one row and
  without overlapping.
- **Presentation layout and icon family tests** (`WeatherPresentationUiTest`, 12 tests):
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
  the new symbols and their indicators render in the light **and** the dark theme; and the shipped
  vectors are parsed from the installed package with `XmlPullParser` to prove the icon family:
  **every one of the 32 conditions in all its family and intensity styles loads a real drawable**,
  the rain and snow symbols draw **one, two and three** precipitation marks whose path geometry is
  byte-identical inside a family (drops filled, flakes stroked), every cloudy icon reuses the same
  `48 × 48` viewport and the same cloud silhouette, and every colour token of the palette stays
  visible on the shipped light **and** dark card and window backgrounds.
- **Icon gallery tests** (`app/src/androidTest/.../ui/gallery`, 3 tests): the debug gallery covers
  every `WeatherCondition` exactly once plus the wind-warning combinations, and it renders every
  row at every comparison size (39 rows × 4 sizes × 4 backgrounds = 624 icon nodes in the
  semantics tree). A third test is a development tool rather than an assertion: it writes one PNG
  per gallery page into the app's files directory for visual review (see
  [Previewing the whole family](#weather-icon-design-system)).

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

### Home location switching, icon contrast and narrow rows

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

### Readability, precipitation intensity and wind indicators

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

### Flat Color weather icon family (current iteration)

Same emulator (`Medium_Phone_API_37.0`, API 37, 1080×2400), real API traffic (home Uljanowsk,
"Schneegriesel"). The debug gallery was rendered on the device by the debug screenshot test, once
per theme (`adb shell cmd uimode night yes|no`), and the 20 pages per theme were pulled with
`adb exec-out run-as com.example.weatherapp tar -c -C files/icon_gallery .`. The dashboard was
captured in light and dark. All statements below come from looking at those images.

- **One family, one grid.** In the light gallery the sun, crescent, partly cloudy and overcast
  rows show the same cloud silhouette with its offset shadow at 24, 32, 48 and 72 dp, the sun with
  its accent ring and eight rays, and the crescent opening to the lower left. Nothing shifts when
  the size changes; only the palette changes between the light and the dark section.
- **Intensity is a count, not a redraw.** Light/moderate/heavy rain show one, two and three
  identical drops; light/moderate/heavy snow one, two and three identical flakes; snow grains
  three dots; drizzle five dots; freezing rain one drop plus one flake; thunderstorm the bolt;
  hail the bolt plus two dots. The marks are the same shape at every size and in every icon of a
  family.
- **The wind indicator behaves.** In the wind rows the glyph sits in the free upper end corner of
  the symbol, at about a third of its size, without a background disc. It does not touch the sun,
  the crescent, the cloud or any precipitation mark, and it stays legible at 24 dp (9 dp glyph).
  Strong wind is neutral, storm is red, in both themes.
- **Dark theme.** The dark pages show the identical geometry with the night palette: near-white
  snow marks, lighter blue drops and clouds, brighter sun and moon; the marks are clearly visible
  on the dark card.
- **Header gradients.** The two gradient sections show the symbols as the hero card draws them.
  Clouds with their shadow, the bolt, the snow marks and both wind colours read well; the blue
  rain drops are the weakest element on the blue gradients (documented below).
- **Dashboard, light and dark.** The 76 dp hero symbol, the hourly chips and the day card all use
  the new family; the snow-grains hero shows cloud plus three dots, the chips show flake and dot
  marks. No status strip appears below "Zuletzt aktualisiert", and no symbol is missing.
- Logcat during the runs: no `FATAL EXCEPTION`, no resource-loading errors, no application
  warnings — only the usual emulator-side notices and the test harness's own activity logging.

Not verified in this iteration: TalkBack reading the new wind glyph labels, a live thunderstorm,
hail or strong-wind day on the device (the gallery covers those states, the dashboard did not
show them), font scales ≥ 1.5, tablets and physical devices.

### WetterPlus branding, navigation, appearance and information pages (current iteration)

Same emulator (`Medium_Phone_API_37.0`, API 37, 1080×2400), real API traffic, screenshots
`app/build/verify/n01…n15*.png`. The app data was cleared first, so this run also covers a first
start.

- **Launcher icon and name** (`n13_launcher_drawer.png`): the app drawer shows the new adaptive
  icon — deep blue field, warm sun with its rays, soft blue cloud in front of its lower right —
  masked as a clean circle with nothing cut off, and the label reads **WetterPlus**. Tapping it
  starts the app. The template robot and the density bitmaps are gone.
- **First start** (`n01_first_start.png`): the setup screen renders with the weather symbol; no
  crash, no blank frame.
- **Dashboard, light** (`n02_dashboard_light.png`): the toolbar title is **WetterPlus** with the
  three actions (refresh, place, gear) on one line; the location "Hamburg / Freie und Hansestadt
  Hamburg" is still shown inside the current-weather card, and no status strip appears below
  "Zuletzt aktualisiert".
- **Settings** (`n03_settings_light.png`): "← Einstellungen" with two rows — "Darstellung / Hell,
  Dunkel oder Systemeinstellung" and "Infos / Version, Wetterdaten und Lizenzen" — each with an
  icon and a chevron; nothing expands in place.
- **Appearance** (`n04_appearance_light.png`, `n05_appearance_dark_pinned.png`): the three options
  with their explanations; tapping **Dunkel** turns the whole app dark in the same frame while the
  system stays light, and the status bar icons stay light on the dark background.
- **Theme follows the system again** (`n14_appearance_system_light.png`,
  `n15_appearance_system_dark.png`): after choosing **Systemeinstellung**, `cmd uimode night no`
  and `night yes` move the app between light and dark.
- **Persistence across a restart** (`n12_restart_persisted_dark.png`): with the system in light
  mode and the app pinned to dark, force-stop and a cold start come up dark — the stored mode is
  read before the first frame is drawn.
- **Information page** (`n06_about_dark_top.png`, `n07_about_licences.png`): "Anwendung" shows
  **WetterPlus**, Version **1.0.0** and Build **2** — the values of the installed package; the
  source cards name the forecast API, the geocoding API, the model selection ("Welches Modell
  tatsächlich liefert, hängt vom Ort ab") and the GeoNames database, followed by the
  "Wetterdaten von Open-Meteo.com" link and the licence list.
- **Licence detail** (`n08_licence_detail.png`): "← Wetterdaten von Open-Meteo" with
  "Lizenz: CC BY 4.0", "Urheber: Open-Meteo.com", the German summary, the offline note and the
  "Im Browser öffnen" button.
- **Location manager** (`n09_locations_dark.png`, `n10_locations_suggestions.png`): the header
  arrow replaces the floating button — `uiautomator` reports **exactly one** "Zurück" node, at
  the top left — the search field stays fixed, the live suggestions still float over the list,
  and the bottom of the list is no longer padded for an overlay.
- **Back hierarchy, checked by reading the header title after each arrow press**: licence detail
  → "Infos" → "Einstellungen" → dashboard. System back was used for the same path in the
  instrumented test.
- **Back order inside the location manager** (unchanged behaviour): first press closes the
  keyboard, second closes the suggestions (the manager and its title stay), third returns to the
  dashboard, which still shows the same location and data.
- Logcat for the whole session: empty crash buffer, no `FATAL EXCEPTION`, no warning or error
  logged by the application process.

Not verified in this iteration: TalkBack behaviour of the new screens (the dashboard is marked
hidden from accessibility while covered, but no screen reader was run), the themed-icon
(monochrome) rendering on a launcher that enables it, tablets and physical devices, and font
scales ≥ 1.5 on the new settings pages.

## Known limitations

- **Theme:** a cold start with a pinned light/dark choice can show one frame in the system scheme
  before the stored mode is applied, because the stored value is read asynchronously from DataStore.
  The application-scoped `AppearanceViewModel` and its eager creation keep that window as small as
  practical; it is not eliminated.
- **Licences:** the About screen carries the attribution, the licence identifier, the author and a
  German summary of every entry offline. The *full* licence text is opened in the browser, so on a
  device without a network the user reads the summary rather than the complete legal text. Test-only
  dependencies (JUnit, androidx.test, Espresso) are not listed in the app, because they are not
  shipped in the APK.
- **Attribution placement:** Open-Meteo asks for "Weather data by Open-Meteo.com" with a link where
  weather data is displayed. The app places that notice with a link on the information page rather
  than on every card of the dashboard, which keeps the forecast layout unchanged.

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
- The screen the user is on survives a configuration change — `AppBackStack` is `rememberSaveable`
  — but a scroll position does not: the dashboard column and the favourite list keep their state in
  a plain `rememberScrollState` / `LazyColumn` state, which is not saved, so a rotation or a theme
  switch returns those lists to the top. The saved locations, the selection and the pending search
  are held by the ViewModel and DataStore and survive.
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
- The wind badge is a static glyph in the free upper end corner of the weather symbol, without a
  background disc. It marks *that* a threshold was exceeded and which measurement caused it; it is
  not an official weather warning, has no duration or area, and is not de-duplicated against the
  DWD or Met Office warning services.
- **Contrast on the header gradient is a deliberate trade-off.** The hero card draws its symbol on
  a coloured gradient, and the blue precipitation tokens (`weather_rain`, `weather_hail`) reach
  only about 1.3–1.6:1 there: the drops are recognisable as drops, but they are the weakest
  element of a rainy header. The palette test checks every token against the shipped card and
  window surfaces of both themes (≥ 1.5:1), not against the gradient, because a rain blue that
  cleared 3:1 on the gradient would leave the flat palette and read as a different weather colour.
- For the same reason the header uses its own wind palette: `HeaderStormRed` (`#FF6F60`) reaches
  about 1.6–1.9:1 in the band the symbol occupies, while the neutral `HeaderWindStrong`
  (`#D8E4EE`) stays above 4:1. A storm red that met 3:1 on those gradients would be a pale salmon
  that no longer reads as a warning. The level is therefore also carried by the text
  ("Sturmwarnung: …" in the hourly detail sheet) and by the accessibility label, and the unit test
  asserts ≥ 3:1 for the neutral glyph and ≥ 1.5:1 plus "red is redder than green" for the storm
  glyph on every header.
- The icons are static, flat vectors: no animation, no gradient inside a symbol, and only the two
  theme palettes. A new condition needs a new drawable plus a mapping entry; the mapping is
  exhaustive and tested, so an unmapped state cannot appear.
- The gallery exists only in the debug source set and its activity is not exported, so a visual
  review of a release build has to go through the dashboard. The gallery PNGs written by the
  screenshot test are review artefacts under `app/build/`, not part of the repository.
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
