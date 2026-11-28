# WetterPlus

Native Android weather application written in Kotlin and Jetpack Compose. It shows live
weather data from [Open-Meteo](https://open-meteo.com/) for a configurable home location, any
number of favourite locations and any place that was just looked at, caches responses locally,
and works offline as long as a cached forecast exists.

The application is named **WetterPlus** everywhere the user sees it — launcher label, system
application info and the dashboard toolbar — while the application ID and package name stay
`com.example.weatherapp`, so an installed build updates in place instead of installing beside it.

All user-visible text is German; source code, comments and this documentation are English.

## Feature summary

| Area | Behaviour |
| --- | --- |
| Current weather | Temperature, condition, feels-like, today's min/max, humidity, precipitation, cloud cover, wind speed/direction/gusts, pressure. Sustained wind and gusts are checked against the wind warning thresholds and, when exceeded, marked on the weather symbol (see "Wind and gust indicators"). Next to the location name sits the **favorite star** of the place that is on screen: outlined and neutral while it is not saved, filled in warm gold once it is a favourite, and always filled for the home location, which is saved by definition. Tapping it saves the place or takes it out of the favourites again. |
| Hourly (rolling) | Horizontal strip with a rolling 24-hour window: it starts at the hourly interval that **contains** the current time, highlights it, labels every day the window touches ("Heute", "Morgen", weekday) and moves on its own when the hour changes. If that current hour is missing from the response, the earliest later hour starts the window; an already elapsed hour is never used as the start, and if the payload only contains elapsed hours the strip shows the unavailable-data note instead of outdated values. The window covers the current interval plus the following 23 hourly intervals by absolute timestamp, so a gap shortens the list rather than stretching the horizon. Every chip has the same outer height and the same internal layout whether or not it carries a day label, and the precipitation probability is rendered in the same size as the clock time so that "100 %" is never cut off. Tapping an hour opens a "Stundendetails" bottom sheet with all values for that hour, including a plain-text wind warning when that hour exceeds a threshold. |
| Days 1–2 | Two detailed cards ("Morgen", "Übermorgen") with condition, precipitation probability/amount, wind, gusts, sunshine and day length. Both cards are tappable ("Details" with a chevron): they open a **Tagesverlauf** bottom sheet with the day split into Vormittag, Nachmittag, Abend and Nacht, aggregated from the hourly data of that day (see [Day periods](#day-periods-vormittag-nachmittag-abend-nacht)). |
| Days 3–15 | Compact list ("Ausblick") with weekday, date, condition icon, precipitation probability/amount and the min/max temperatures as numbers; up to the 16-day forecast horizon of the API. The written condition label and the temperature range bar were removed from these rows: the icon, the numbers and the wind marker carry the same information in less space, and the full wording stays in the detailed cards and in the accessibility label of the icon. |
| Locations | Three roles, three sections, always in this order: **Hauptstandort** (house icon), **Favoriten** (filled star) and **Verlauf** (outlined star) — the places that were only looked at. Managed on a dedicated full screen instead of a bottom sheet: the title and the search field stay fixed at the top, the location list scrolls on its own, and search results appear in a floating dropdown directly under the search field, overlaying the list without moving it. The screen is entered and left through the **back arrow in its header** ("← Orte verwalten"), like every other secondary screen; the system back button first closes the suggestions. **Looking at a place never saves it**: tapping a row — a suggestion, a favourite or a history entry — shows that location on the dashboard right away and leaves the screen. Saving is an explicit decision made with a star, either on the current weather card or on a history row. Un-saving a favourite moves the place into the history instead of forgetting it, and saving a history entry takes it out of the history, so a place is never in both lists at the same time. The history is sorted most recent first, holds at most `LocationSettings.MAX_RECENT_LOCATIONS` (30) entries without duplicates and drops only its least recently used entry when it is full; a single entry can be removed, and **Alle löschen** clears the whole section behind a confirmation dialog. Favourites and the home location are never affected by it. Home is a single protected slot: promoting a favourite or a history entry to home swaps the two entries, the previous home moving into the favourites at exactly the position the new home had, and the new home becomes the displayed location; no saved place is ever dropped and none appears twice. In the list the home row carries no star, no delete icon and no menu — the role can only be moved to another place, never removed — while on the card its star is always filled and only states that this place is always saved. Reordering stays limited to favourites. Duplicate places are rejected (same id or same rounded coordinates) and reported without leaving the screen. The last displayed location is restored on the next start. |
| Refresh | Pull-to-refresh, toolbar refresh button (both bypass the cache), automatic refresh when the app returns to the foreground, and a silent background refresh when the cached response belongs to a previous calendar day in the location's time zone. The hourly window itself advances every hour without any network request. |
| Offline | The last response per location is cached; with no network the cached data is shown instead of an empty screen. |
| Appearance | Material 3, rounded cards, condition-dependent gradient header, custom vector weather icons, German date/number formatting, 24-hour time. The theme is a user choice on its own screen: **Systemeinstellung / Hell / Dunkel**, applied immediately, stored, and restored on the next start (see [Appearance and theme](#appearance-and-theme)). |
| Navigation | One hierarchy for every secondary screen: dashboard → location management, dashboard → settings → appearance, dashboard → settings → privacy, dashboard → settings → information → licence detail. Each secondary screen carries a back arrow in its header, the system back button follows the same path, and the dashboard keeps its state (scroll position, loaded forecast) while a secondary screen is open. |
| Settings and information | A settings screen with three entries in this order ("Darstellung", "Datenschutz", "Infos"), the appearance page described above, and an information page with the installed version and build number, the weather services behind the data with their links, and the licences of the data and of the shipped libraries — readable offline, with the full licence text opening in the browser. |
| Privacy and backup | A dedicated **Datenschutz** screen (settings → privacy) describing what the app stores, what it sends to the weather service and how backups are handled, with a link to the weather service's own terms and privacy notice. Home location, favourites, ordering, selected location and appearance are eligible for the Android backup; the forecast cache and its database side files are excluded because they can be fetched again (see [Privacy, local storage and backup](#privacy-local-storage-and-backup)). |
| Launcher icon | A custom adaptive icon (warm sun with a soft cloud over it on a deep blue field) with a monochrome layer for themed icons; no template artwork and no bitmaps, so it renders from the same vector on every density. |
| Home-screen widgets | Three Glance widgets — **Aktuelles Wetter** (current conditions plus two or four information tiles), **Tagesverlauf** (four rolling day periods) and **3-Tage-Vorschau** (heute / morgen / übermorgen). Every instance is configured separately with **Hauptstandort**, **Aktueller Ort** or **Fester Ort**, the layouts adapt to the width the launcher offers, light and dark follow the system, and a tap opens the app at the widget's location. Updates come from one shared WorkManager schedule and the app's own cache policy — no extra permission, no foreground service, no second API (see [Home-screen widgets](#home-screen-widgets)). |

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

The dashboard, daily cards, period details, extended forecast and widgets use
`WeatherFormatter.temperatureRange` for minimum and maximum pairs, for example
`-12/-10 °C`. The pair shares one Celsius unit and preserves German number formatting
and the existing rounding. Two reported values that round to the same number still form
a pair. A single reported value is shown on its own, and two missing values show an en dash.
Clock and date ranges keep their own separators.

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

### Day periods: Vormittag, Nachmittag, Abend, Nacht

Tapping the "Morgen" or "Übermorgen" card opens `ui/dashboard/DayForecastDetailSheet.kt`, a
Material 3 `ModalBottomSheet` that describes the selected day in four periods. It is the daily
counterpart of the hourly "Stundendetails" sheet and deliberately a separate component: an hour is
listed, a period is *aggregated*, and mixing the two would hide which number is measured and which
one is computed.

**The periods** (`domain/DayPeriodForecast.kt`, `enum DayPeriod`) are calendar periods of the
**selected location**, resolved to absolute instants with that location's time zone:

| Period | Local time range |
| --- | --- |
| Vormittag | 06:00–11:59 |
| Nachmittag | 12:00–17:59 |
| Abend | 18:00–21:59 |
| Nacht | 22:00–05:59 **of the following calendar day** |

- The night belongs to the day it *starts* in: the night of Monday runs Monday 22:00 → Tuesday
  06:00, and the early hours of Monday itself belong to Sunday's night. Nothing merges the two.
- Boundaries are computed as `LocalDate.atTime(...).atZone(zone).toInstant()`, so midnight
  transitions, daylight saving transitions and zones with a fractional offset (+05:30, +05:45)
  all resolve to the right absolute instants. A wall clock time a DST gap removes is resolved to
  the first instant after the gap rather than to the previous day.
- A period is never assumed to hold a fixed number of hours: the night across the spring
  transition holds 7 hourly points, the one across the autumn transition holds 9 (both
  occurrences of the repeated 02:00), and a period shorter than its own duration is marked
  incomplete instead of being quietly padded.
- The sheet uses the **complete hourly series** of the location, which `ForecastSections.allHours`
  carries along; the rolling 24-hour strip is only a slice of it, so "Übermorgen" is as complete as
  "Morgen". Opening, scrolling and closing the sheet never send a request — asserted by
  `DayForecastSheetNavigationTest`.

**Aggregation rules** (all in `buildDayPeriodForecast`, none in a composable):

| Value | Rule |
| --- | --- |
| Temperature | Minimum and maximum of the available hourly temperatures, printed as `7/12 °C`. One known value is printed alone. |
| Precipitation probability | **Maximum** of the hourly probabilities, labelled "Max. Regenw." — the highest hourly value, not a combined "at some point during the period" probability. Hourly probabilities are never multiplied together. |
| Precipitation amount | Sum of the hourly amounts inside the period. A period without a single reported amount stays an en dash, and a period whose hourly series is shorter than the period says so in text instead of presenting a partial sum as a total. |
| Wind | Arithmetic mean of the available sustained hourly wind speeds ("Wind im Mittel"). Missing, negative and non-finite values are ignored. |
| Gusts | Maximum of the available hourly gust values ("Böen bis"), kept separate from sustained wind. |
| Wind warning | `WindWarningClassifier` on the strongest sustained wind and the strongest gust of the period, so the indicator and its text name the measurement that caused it. |

**Representative condition.** A period is not represented by its first hour, nor by a single brief
extreme. `representativeCondition(hours)` works in five documented steps:

1. Hours without a condition, and `UNKNOWN`, carry no information and are skipped.
2. Every remaining hour votes for its coarse condition group (clear, partly cloudy, overcast, fog,
   drizzle, rain, snow grains, snow, freezing precipitation, thunderstorm, hail) with a weight of
   1, 1.5 or 2 depending on how heavy its reported precipitation is — so steady rain outweighs an
   equal number of quiet hours, and cloud cover enters through the groups themselves.
3. The group with the highest total weight wins; ties go to the more significant group, which keeps
   the result independent of the order of the payload.
4. Inside the winning group the most frequent concrete WMO condition wins (ties to the heavier one),
   so one heavy hour cannot speak for a whole period.
5. The day/night variant follows the period: the majority of the hours that report a sun state
   decides, which is what makes the night period show a moon.

A brief but significant event is still not hidden: `severeCondition` reports the strongest
thunderstorm or hail of the period, and when it differs from the representative condition the card
names it separately ("Zusätzlich möglich: Gewitter").

**Missing data.** A period with no hourly point at all says "Für diesen Zeitraum sind keine
Stundendaten verfügbar." instead of showing zeros; a period with some values shows exactly those
and nothing more. A reported zero is a measurement, not a gap: an hour that says "0 mm"
contributes to the sum, and only an hour whose amount is absent is skipped — so a dry period reads
"0 mm" while an unreported one reads "–". The sheet ends with one footnote that states what the
three numbers mean, and the weather symbol of each period carries a German accessibility
description with the condition and the temperature range, so nothing depends on the icon or a
colour alone.

**Layout at large text sizes.** The four values of a period sit in one row of four columns at the
normal system font size. From a system font scale of 1.3 they are laid out as two rows of two, so
a column never becomes narrow enough to split a unit like "12 km/h" across lines. The sheet itself
scrolls, which keeps all four periods reachable on a small screen and at every font scale.

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
                     PrivacyScreen, LocationSetupScreen)
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
  is reported alongside it, and usable cache rows are never deleted — only rows of places that no
  location role refers to any more, see the retention rules under
  [Privacy, local storage and backup](#privacy-local-storage-and-backup). HTTP/IO/serialisation
  errors map to a small `DataLoadFailure` set with German messages; corrupted cache rows are
  discarded.
- **Three location roles, one list.** `LocationSettings` holds `homeLocation` (one nullable
  slot), `favorites` (in the user's order) and `recentLocations` — the "Verlauf" of places that
  were only looked at, newest first and capped at `LocationSettings.MAX_RECENT_LOCATIONS` (30).
  `savedLocations` is home plus favourites, i.e. what is really stored; `selectableLocations`
  adds the history, and that is the set the dashboard is allowed to display — `hasSelectableLocation`
  is what decides whether the setup screen is shown, so a file with places but no home location is
  still usable. `retainedLocationIds` is the same set plus the selection, and it is the single
  answer to "is anybody still referring to this place", which the cache cleanup uses. A place is
  identified by `SavedLocation.isSamePlaceAs(other)` — its id or its rounded coordinate key — so
  the same city reached through a different search hit never appears twice. `LocationRepository`
  serialises every mutation through one `Mutex` and returns the resulting settings, which is what
  keeps the three lists consistent: favouriting takes a place out of the history, un-favouriting
  puts it at the top of the history, promoting to home moves the previous home into the favourite
  slot the new home vacated, and every mutation ends in `keepValidSelection`, which falls back to
  another place only when the selected one has disappeared from *all three* lists. The star is one
  repository call, `toggleFavorite(location)`, which decides *inside* the lock whether the place is
  a favourite, the home location, a history entry or unknown and reports that decision back
  (`FavoriteToggle.ADDED / REMOVED / HOME_LOCATION / UNCHANGED`) — the UI never reads the list,
  changes it and writes it, so two quick taps are two real state changes in a row. Deleting the
  home location is refused by the repository itself, not only hidden in the UI.
- **Settings are normalized when they are read.** `LocationSettings.normalized()` is applied by
  `SettingsStore.decode()`: a place that sits in more than one role keeps the role with the higher
  precedence (home, then favourite, then history), duplicates are found by `isSamePlaceAs` and
  never by display name, the history is capped and keeps its order, and a selection that points at
  a place nobody has any more is repaired to a place that does exist. It is idempotent and it never
  invents a home location. The corrected state is not written back on read — the next real change
  persists it — so opening the app does not touch the preferences file.
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
    ├── Datenschutz
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
  suggestions, the second leaves the screen. While the "Verlauf löschen?" confirmation dialog is
  open, a back press closes the dialog and nothing else.
- Inside the location list a **row tap is the only thing that selects a place** and returns to the
  dashboard. Every other control — the star, the delete icon, the three-dot button — acts on its
  own row and stays on the screen. The less frequent actions ("Als Hauptstandort setzen",
  "Nach oben", "Nach unten") live in a compact Material 3 `DropdownMenu` behind the three-dot
  button, so a row on a narrow phone carries at most three icons; the width those icons need is
  declared to the row layout, which moves them onto their own line instead of squeezing a long
  place name into a few characters. The home location's row carries **no action at all**: no star,
  no delete icon, no menu. It is still selectable by tapping it, and the only way to move the home
  role is "Als Hauptstandort setzen" on another place, which turns the previous home into a normal
  favourite. That removes the one action that used to be able to leave the app without a home
  location.
- The toolbar title is the application name, not the location; the location stays visible in
  the current-weather card, which is the only place it is needed. It is set in `headlineSmall`
  (24 sp), one step above the `titleLarge` (21 sp) every secondary header uses, so the name of
  the app is never smaller than the name of a page below it. The title keeps its own column
  inside the standard 64 dp `TopAppBar`, so it ellipsizes instead of reaching into the three
  action buttons, and it scales with the system font size.
- The two detailed day cards open the daily detail sheet described in
  [Day periods](#day-periods-vormittag-nachmittag-abend-nacht). The sheet is a bottom sheet of the
  dashboard, not a destination of the back stack: the dashboard stays composed underneath it, and
  closing it (close button, swipe down or system back) leaves the scroll position and the loaded
  forecast untouched.

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

## Home-screen widgets

Three Glance widgets (`androidx.glance:glance-appwidget`) ship with the app. They are added from the
launcher's widget picker, every one of them opens the same configuration screen when it is placed,
and each **instance** has its own location: two "Aktuelles Wetter" widgets can sit next to each other
and show two different places. Nothing about the widgets changes the dashboard — they read the same
repository, the same cache and the same cache policy that the app itself uses.

| Widget | Classes | Content |
| --- | --- | --- |
| **WetterPlus – Aktuelles Wetter** | `widget/CurrentWeatherWidget`, `CurrentWeatherWidgetReceiver` | Location, update age, current temperature, condition, Flat Color symbol with wind marker, and two or four prioritized metrics. |
| **WetterPlus – Tagesverlauf** | `widget/DayPeriodWidget`, `DayPeriodWidgetReceiver` | Four consecutive day periods — Vormittag, Nachmittag, Abend, Nacht — starting with the period that contains the local time at the widget's location, so the row always reads "the rest of today and what comes next". |
| **WetterPlus – 3-Tage-Vorschau** | `widget/ThreeDayWidget`, `ThreeDayWidgetReceiver` | Heute, Morgen and Übermorgen with the daily min/max, the day's condition symbol and its precipitation, taken from the daily block of the same response the dashboard uses. |

Every value a widget prints goes through the app's own aggregation and formatting code:
`buildDayPeriodForecast`, `WeatherFormatter`, `shortDescriptionRes`/`weatherIconRes` and
`WindWarningClassifier`. A widget therefore never shows a number the app would not show, and a missing
value stays the en dash of the app instead of a zero.

### Sizes and launcher previews

Each provider targets **3 × 2** launcher cells by default and permits horizontal resizing to
approximately **2 × 2**. The metadata declares a minimum of 146 × 148 dp and a cap of 300 × 220 dp.
Launcher cell sizes and enforcement vary. Glance uses the actual host dimensions and selects the
expanded layout from 240 dp width. Extra height is distributed within the same layout. The
day-period grid has two equally weighted rows, and the three-day forecast has three.

| Widget | Compact | Expanded |
| --- | --- | --- |
| Aktuelles Wetter | Location, update age, temperature, condition and two metrics, with humidity and wind first | Up to four metrics |
| Tagesverlauf | Four cards with symbols, local day labels, temperature and precipitation | Adds time ranges and room for both precipitation measures |
| 3-Tage-Vorschau | Today, tomorrow and the day after with symbols, temperature ranges and precipitation probability | Adds dates, conditions and precipitation amount |

Each provider declares a dedicated Android preview layout with representative 3 × 2 content.
The previews use the existing Flat Color icons and a matching light and dark palette. Sample values
live in picker-only resources and require neither a saved location nor a network connection.

### Configuration and first render

The launcher opens **Widget einrichten** with an already bound Android App Widget ID. Home and
selected modes require a resolvable app location. A fixed location can be selected from saved
places or searched for on a fresh installation. Choosing a search result closes the suggestions.
The selected place and Save and Cancel controls remain visible below the scrollable list.
Supported launchers also offer reconfiguration for placed widgets.

The configuration DataStore stores one record per ID in
`files/datastore/widget-configurations.preferences_pb`. A fixed entry retains its own location.
Cancel returns the original ID and leaves existing records intact. Save validates the current
location settings, persists the record, requests a cache-only Glance update for that exact ID
and queues its initial refresh before returning `RESULT_OK`.

In 1.2.0, Glance could reuse an active composition that had captured empty configuration and
weather. The 700 ms redraw and Glance's all-widget lookup could also miss the newly bound
instance. Compositions now observe configuration, app settings and cache invalidation. They
read local data on startup and throughout the session. Workers also call `update()` for affected
live IDs to wake sessions that have ended. Deleting a widget removes its record and initial
work. In-flight results target only currently bound widgets that still resolve to that location.

### The rolling day periods

The Tagesverlauf widget does not show "today's four periods"; it shows the next four **period slots**
beginning with the one that contains the local time at the widget's location.
`domain/RollingDayPeriods.kt` computes those slots:

```
Vormittag 06:00–11:59 · Nachmittag 12:00–17:59 · Abend 18:00–21:59 · Nacht 22:00–05:59
```

`currentDayPeriodSlot` maps the local clock hour to a period, and a night belongs to the calendar day
it **starts** in — the night from Monday to Tuesday is Monday's night, which is what makes the row
"tonight" instead of "tomorrow night". The four periods are then cycled, and every fourth slot
advances the date, so at 19:00 the widget offers Abend (today), Nacht (today), Vormittag (tomorrow)
and Nachmittag (tomorrow), while at 03:00 the first card is the night that began yesterday.
`buildRollingDayPeriods` hands each slot to the existing `buildDayPeriodForecast`, so the aggregation
rules (maximum of the hourly precipitation probabilities, sum of the amounts, mean wind, maximum
gusts) are the documented ones from [Day periods](#day-periods-vormittag-nachmittag-abend-nacht) —
nothing is re-aggregated for the widgets.

All of it is computed in the time zone of the location, never the phone's: `RollingDayPeriodsTest`
covers Asia/Kolkata (+05:30) and Asia/Kathmandu (+05:45) boundaries, and the two daylight saving
transitions of Europe/Berlin. On 29 March 2026 the night loses the hour that the clock jumps over,
which is why that window expects seven hourly points instead of eight; on 25 October it gains one.
The window ends exactly where the next one begins at every start hour of the day, so no hour is
skipped and none appears twice. A period the forecast does not reach keeps its card and reports that
it has no data instead of silently dropping a day.

### Background updates

One WorkManager job refreshes all widget types every 30 minutes. Launcher update requests,
app foreground and app weather changes can also request a shared refresh. A newly configured
widget gets an independent initial request. Overlapping workers serialize the synchronization,
reuse the shared cache and load each distinct active location once per pass. Fresh forecasts
need no network request.

Workers publish cached weather before synchronizing. A failed request keeps available data
visible with a saved-data label and leaves a clear unavailable state where there is no cache.
WorkManager retries with backoff. Clock, date and time-zone events redraw from cache. A package
update reconciles widget IDs and the refresh schedule. WorkManager reschedules persistent work
after restart and may defer it under Android's background restrictions. Periods and dates
are computed in the forecast location's time zone. WorkManager cannot guarantee exact period
transition or local midnight times.

The orphaned-cache cleanup includes locations pinned by widgets. These forecasts stay available
even when a place leaves the app's favourites or history.

### Appearance and taps

Widgets follow the system's light or dark mode through Glance's day/night colour providers: every
colour of `widget/presentation/WidgetVisuals.kt` carries a light and a dark value, and Glance resolves
the pair while it builds the widget. The values are the app's own palette tokens from
`ui/theme/Color.kt` — the same surface variants, on-surface colours, accent and storm red — so a widget
next to the app cannot drift into different shades of the same weather; only the light tile colour
(`WidgetTileLight`) is new, because a widget tile has to sit one step *lighter* than its surface while
the app's cards sit lighter than a white background. Glance's other colour factory takes a colour
*resource*, but that factory is restricted to the Glance libraries themselves — calling it from the app
is a lint `RestrictedApi` error — so an application has to pass the explicit day/night pair, and the
widget palette deliberately does not duplicate the icon palette in
`values/colors.xml`.
Icons are the same Flat Color vectors the app draws, with the wind glyph
overlaid when a wind threshold is exceeded, and the storm colour marks the same warning.

Tapping a widget opens WetterPlus at its current configured location. The activity resolves the
live widget ID against the local configuration before selecting a place. Opening a fixed place can
move it into the history, like a search result. Activity recreation resumes the persisted selection.

## Versioning

`app/build.gradle.kts` carries the version: `versionName = "1.2.2"` in `MAJOR.MINOR.PATCH` form
and a positive `versionCode` that only ever increases (currently 7, because the first published
build of this repository was 1). The About screen does not repeat those numbers: it reads
`PackageInfo.versionName` and `longVersionCode` from the installed package, so the displayed
values cannot drift away from the build that is actually running.

The rule for the next change is written into `AGENTS.md`: MAJOR for incompatible behaviour or a
stored format older builds cannot read, MINOR for added user-facing functionality that stays
compatible, PATCH for fixes, and `versionCode` increases by one per published build.

| Version | versionCode | What changed |
| --- | --- | --- |
| 1.0.0 | 2 | First published build: home location plus ordered favourites, rolling hourly window, day periods, cached offline forecasts, appearance and privacy screens. |
| 1.1.0 | 3 | Location selection reworked: a tapped search suggestion now opens the dashboard immediately without saving anything, the current-weather card carries a favorite star, and a persistent "Verlauf" of viewed places was added next to the protected home location and the favourites. |
| 1.1.1 | 4 | Stabilization of that workflow, no new features: the home location can no longer be un-set and is refused by the delete path, a newly selected place is named immediately while its weather loads, cached forecasts of places that left the history are removed instead of accumulating, the favorite star now toggles atomically (two quick taps are two real changes), and stored locations are normalized when they are read. |
| 1.2.0 | 5 | Three home-screen widgets: **Aktuelles Wetter** with up to nine information tiles, **Tagesverlauf** with four rolling day periods, and **3-Tage-Vorschau**. Every instance is configured on its own (home location, current location or a pinned place), they share one WorkManager schedule and the existing cache, and a pinned place keeps its cached forecast even after it left favourites and history. The widget configuration lives in its own DataStore file and is included in the Android backup. |
| 1.2.1 | 6 | First placement loads cached weather and refreshes the correct instance. Configuration validates locations and keeps actions visible. All three widgets have two-cell layouts and launcher previews. |
| 1.2.2 | 7 | Slash-separated temperature ranges, recoverable location and widget configuration saves, forecast and cache validation, stable appearance changes and safer widget navigation. |

## Reliability audit for 1.2.2

The code review covered startup and screen restoration, location roles and search, DTO mapping and
request cancellation, absolute timestamps and local aggregation, DataStore and Room, backup rules,
widget lifecycle and WorkManager, and the range layout and accessibility paths. Regressions cover
the corrected behavior alongside the existing navigation, DST, location retention and widget suites.

| Finding | Severity | Correction and coverage |
| --- | --- | --- |
| An I/O failure during a location mutation escaped the ViewModel and could terminate the app. | Critical | Report a German retry message and preserve setup search results. A deterministic failed setup test verifies recovery. |
| Forecast periods containing only timestamps made an otherwise empty response cacheable. | High | Drop measurement-free rows and reject unusable cached responses. A repository regression verifies that a useful previous forecast survives. |
| Rejecting a corrupt cache row could delete a good replacement written after the read. | Medium | Delete only the exact row inspected. A Room regression exercises a replacement with the same fetch timestamp. |
| Dashboard and widget loads of the same location could fetch independently, and cancelled cache reads could fall through to the API. | Medium | Share per-location synchronization in the weather repository and propagate cancellation. Gated concurrent loads and cancelled storage reads have regression tests. |
| Earlier appearance writes could briefly replace a newer choice or report a failure for it. | Medium | Serialize writes and associate feedback with the current selection. A gated-write test checks the intermediate UI state. |
| Geocoding accepted out-of-bounds coordinates, a malformed integer offset could wrap into a valid one, and reported zero sunshine or daylight was rendered as unavailable. | Medium | Validate coordinates and offsets before conversion and keep real zero durations. Mapper and formatter tests cover each input. |
| Cache coverage could expire before the last hourly interval ended in a fractional-offset zone. | Medium | Check the end of the absolute interval. A Kathmandu regression verifies reuse up to the boundary and refresh at expiration. |
| Long negative pairs could overflow dense outlook rows at narrow widths and increased font scale. | Medium | Give the range its own line when needed. An instrumented layout test checks a 245 dp dark layout at 150% font scale. |
| Dashboard detail sheets could outlive a location switch, and activity recreation could replay a prior widget launch. Widget taps also trusted location values supplied through an activity intent. | Medium | Scope sheet selection to the displayed location, handle widget launch on initial creation or a new intent, and resolve locations from a currently bound widget's stored record. UI and DataStore tests cover location switches and intent resolution. |
| The hourly effect remained active in a composed but stopped activity. | Medium | Run the clock effect only while the lifecycle is started. Foreground and date rollover behavior retain the existing deterministic ViewModel coverage. |
| A prerequisite could disappear while widget Save was pending. The suspected self-rejection was not reproduced because the old code captured state before setting the saving flag. | Medium | Separate location validity from button state, restore the Save controls with specific feedback, and preserve the prior configuration if storage fails. Tests cover prerequisite changes, valid modes, failed reconfiguration, and retry. |

Widget configuration backup is still keyed by Android App Widget ID. A launcher restore that
assigns different IDs remains an unverified cross-device risk.
[Android widget restore contract](https://developer.android.com/reference/android/appwidget/AppWidgetProvider#onRestored(android.content.Context,int%5B%5D,int%5B%5D))
Cache cleanup across location
preferences and the forecast database is best-effort because the stores do not share a transaction.
WorkManager and launcher timing remain system-controlled.

On the API 37 emulator, live Berlin forecasts showed the new format on the dashboard, daily
cards, period sheet and extended outlook. All three widgets initialized and rendered at both
supported widths. In airplane mode the compact three-day widget and dashboard rendered cached
Berlin data. Changing the dashboard to Tromsø updated the following widget, while the fixed
Berlin widget kept its location and opened Berlin when tapped. After that launch, selecting
Tromsø and recreating the activity at 150% system font scale preserved the newer selection.
With no active network, a cold start after force-stopping the app restored Tromsø and displayed
its cached forecast. All 449 unit tests and 179 instrumented tests passed. Android lint reported
zero errors and 15 existing warnings, and `assembleDebug` produced the installable APK.
Verification screenshots are in
`app/build/verify/wetterplus-1.2.2/`.

## Privacy, local storage and backup

The app documents its own data handling inside the app: **Einstellungen → Datenschutz**
opens a full screen (`ui/about/PrivacyScreen.kt`) with the same header and back behaviour as every
other secondary screen — the entry sits between "Darstellung" and "Infos", and the information page
no longer repeats it. Its text is written from what the code really does, and the section
headings are the ones in `strings.xml`: overview, data stored on the device, forecast requests,
place search, cookies and tracking, storage and backup, and the weather service's own notice,
which links to the official Open-Meteo terms and privacy page
(<https://open-meteo.com/en/terms> — Open-Meteo publishes both on that one page).

**What is stored on the device** (verified with `run-as` on a running emulator):

| Path inside the app data directory | Content | Written by |
| --- | --- | --- |
| `files/datastore/weather-settings.preferences_pb` | home location, favourites with their order, the history of viewed places (`recent_locations`), selected location, appearance (`theme_mode`) | `SettingsStore`, `ThemeStore` (one DataStore file) |
| `files/datastore/widget-configurations.preferences_pb` | one entry per home-screen widget instance: its App Widget id, its location mode and, for a pinned widget, the complete location with coordinates and time zone (`widget_configurations`, one JSON list) | `WidgetConfigStore` (a second DataStore file) |
| `databases/weather-cache`, `weather-cache-wal`, `weather-cache-shm` | one cached forecast per location plus the time it was fetched | Room (`ForecastCache`) |
| `cache/`, `code_cache/` | SQLite lock file, JIT caches, temporary files | the system, not the app |

There is no account, no advertising or analytics library, no crash reporting, no in-app browser,
no cookie storage and no location permission. `INTERNET` is the only permission, and the only two
hosts ever contacted are `api.open-meteo.com` (`v1/forecast`) and
`geocoding-api.open-meteo.com` (`v1/search`), both over HTTPS and without an API key. A forecast
request carries the coordinates of the selected place and the requested variables; a search sends
the entered text. As with any HTTP request, the server also sees the device's public IP address.
OkHttp is configured with timeouts only — no interceptors, no logging.

**Retention.** The cache holds one row per location and is replaced on the next refresh; a
corrupt row is deleted when it is noticed. A cached forecast is dropped only when **no role refers
to that place any more** — not when it merely stops being a favourite, because a place that moves
into the history can still be on screen. The set of places that are still referred to is
`LocationSettings.retainedLocationIds`: the home location, every favourite, every history entry
and the selected place. `MainViewModel` compares that set before and after every location change
and deletes exactly the difference (`dropOrphanedCache`), which covers "remove location", "remove
from history", "clear history" and — the case that used to leak — a history entry that the
30-entry limit evicts while a new place is being opened. Because the history limit is applied
inside the repository, the view model cannot know the evicted id in advance, so the cleanup always
works from the two retained sets. It re-reads the settings once more immediately before the
destructive delete, so a place that became reachable again in the meantime keeps its row; a lost
race can only ever leave one row behind, never delete a live one. On top of that, the app walks the
stored keys once per start (`reconcileOrphanedCache`) and removes whatever nobody refers to, which
also cleans up rows written by an older version. Stale entries are deliberately kept while a
refresh runs and as the fallback when a refresh fails, which is what makes the app usable offline.
The history itself is bounded: at most 30 entries, oldest evicted first, so the preferences file
cannot grow without limit.

Home-screen widgets widen that set in one direction only: the places a widget is **pinned** to are
always retained (`WidgetConfigRepository.fixedLocationIds()`), because the widget holds its own copy
of the location and can show it long after the place left the favourites and the history. Deleting
the widget drops that protection and the row goes away with the next cleanup — the set can only
shrink through user action, never through a bug in the cleanup.

**Backup policy** (`res/xml/backup_rules.xml` for Android 11 and lower,
`res/xml/data_extraction_rules.xml` for Android 12 and newer, both referenced from the manifest):

- Kept: everything the user created — home location, favourites and their order, the history of
  viewed places, the selected location, the appearance choice and the configuration of every
  home-screen widget (mode plus the place a pinned widget shows). They are covered by the default
  "back up the app data directory" rule, so no `<include>` is used anywhere; adding one would
  restrict the backup to the listed paths and could silently drop saved places.
- Excluded: the forecast cache, i.e. `domain="database" path="weather-cache"` plus its `-wal` and
  `-shm` side files (the platform adds `-journal` on its own), and the debug-only
  `files/icon_gallery` folder. All of it can be fetched again.
- `cache/` and `code_cache/` are never part of an Android backup, so they need no rule.
- Android 12 and newer read `dataExtractionRules`; the file configures `<cloud-backup>` and
  `<device-transfer>` identically. For apps targeting Android 12 or newer, `fullBackupContent` is
  ignored for the device-to-device transfer, which is why both files carry the same policy.

What was actually verified and what was not: the rules were parsed on a device by the platform's
own backup code (`bmgr backupnow` with `BackupXmlParserLogging` at verbose level showed
`Found valid data-extraction-rules`, the excluded `weather-cache*` paths and
`files/datastore/weather-settings.preferences_pb` being walked), and a full cycle was run with the
local debug transport — back up, `pm clear`, uninstall, reinstall, first launch: the saved places
came back and the weather was fetched again, while the cache database was not restored. Not
verified: a real Google cloud backup and a physical device-to-device transfer, both of which need
an account and a second device.

## Build and run

Android Studio (or a JDK 17+ and an Android SDK) is required. If `java` is not on `PATH` and
there is no `local.properties`, export the toolchain first:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

```powershell
.\gradlew.bat assembleDebug          # debug APK
.\gradlew.bat testDebugUnitTest      # unit tests (432)
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat connectedDebugAndroidTest   # 171 instrumented + Compose UI tests on a running device
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
  selection fallbacks, search debouncing/short queries, **home switching** and the **history**
  (`LocationRepositoryTest`, 45 tests: the previous home takes the favourite slot the new home
  leaves, the New York City/Hamburg/Dresden/Paris/Madrid sequence keeps all five places, repeated
  home changes, six concurrent taps, duplicates already present in stored state, a single
  favourite, a missing home entry, the new home becoming the selected location, the selection
  surviving a restart, and no write at all when the current home is assigned again. For the
  history: selecting a searched place shows it without favouriting it, the same place twice stays
  one entry, a search hit that matches a saved place by coordinates reuses the saved entry,
  selecting a favourite or the home location leaves the history empty, the list stays sorted by
  most recent use, selecting an entry again moves it to the top, the 30-entry cap evicts only the
  least recently used place, the cap never evicts a favourite or the home location, favouriting
  takes a place out of the history, un-favouriting puts it at the top of the history, the home
  location can never be moved into the history through the favourite toggle, deleting one entry
  leaves the rest and the selection alone, clearing keeps favourites, home and selection,
  clearing or deleting the *displayed* entry falls back to the home location, a history entry
  survives a restart, a history entry can become the home location without duplicating anything,
  and an unknown id changes nothing), the **atomic star** (two rapid taps produce exactly one add
  and one remove, the home location cannot be unfavorited, favoriting a history entry moves it out
  of the history, and a favourite stored under another id is still recognized by its coordinates)
  and the **protected home** (removing the home location is refused and changes nothing, and the
  only home location survives every removal attempt);
  **the location model itself** (`LocationSettingsTest`, 17 tests: an empty settings object,
  `savedLocations` being home plus favourites in their order while `selectableLocations` also
  holds the history, the three role queries answering for one role only, a valid selection
  beating every fallback, the fallback chain home → favourites → history, history alone not
  counting as a saved place but still being selectable, the capacity being one central constant,
  identity being the stored id rather than the display name, and `retainedLocationIds` covering all
  three roles plus the selection); plus **normalization** (`normalized()`, in the same test class: a
  consistent state is unchanged, a place stored twice keeps the role with the higher precedence,
  duplicates are found by place and not by display name, an oversized history keeps its order and
  drops the least recent entries, a selection pointing at a missing place is repaired, the result is
  idempotent and never invents a home location, and an empty state stays empty);
  **row action colours** (`LocationRowActionColorsTest`, 5 tests: the enabled move, promote and
  remove tints reach at least 3:1 contrast against the shipped light and dark backgrounds, the
  disabled tint is clearly weaker yet still visible, and the three emphasis levels are
  `onSurfaceVariant`, `primary` and `error`); `MainViewModel` state transitions
  (`MainViewModelTest`, 40 tests: initial load, location switch, late responses, hourly tick
  without extra request, silent refresh after a local day change, foreground refresh,
  cached-while-refreshing, error states, and removing a location while **also dropping the cached
  forecast of that location** so a deleted place leaves no row behind). For the new flow: a tapped
  suggestion is displayed at once without becoming a favourite and clears the search, the star on
  the card reports the role of the displayed place, the same suggestion twice stays one history
  entry, selecting a favourite or the home location leaves the history empty, the star moves a
  place between history and favourites while it stays on screen, **changing the favourite state
  never triggers another request**, the home location stays saved when its star is tapped,
  un-favouriting the displayed place keeps its forecast in the cache, deleting the displayed
  history entry falls back to the home location and drops only that now unreachable forecast,
  clearing the history keeps favourites, the home location and their cached forecasts, browsing the
  list of saved and recently viewed places causes no extra request either, and two selections while
  the first response is still on its way end with the values of the last one — never a mixture of
  two places); plus the **cache reconciliation** (a history entry that the 30-entry cap evicts loses
  its cached forecast while the surviving entries keep theirs, a row nobody refers to is removed on
  start, and un-favouriting a place keeps its cached forecast) and the **card state while loading**
  (a newly selected place is named, with the right star, before its weather arrives, a failed load
  keeps that name and reports the failure, and two rapid star taps add and remove the favourite
  again while the star of the home location reports that it is protected);
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
- **Day periods** (`DayPeriodForecastTest`, 35 tests): the four period windows as absolute instants
  — Vormittag 06:00–11:59, Nachmittag 12:00–17:59, Abend 18:00–21:59, Nacht 22:00–05:59 — each
  checked at both boundaries (the hour before, the first hour, the last hour, the hour after); the
  night belonging to the day it *starts* in, holding the early hours of the next calendar day while
  the early hours of the selected day belong to the previous night; aggregation (minimum and maximum
  temperature, the **highest** hourly precipitation probability instead of a combined value, the sum
  of the hourly amounts with a reported zero counting as a measurement, the arithmetic mean of the
  sustained wind, the highest gust, and a wind warning caused by gusts alone); missing values staying
  missing (no temperature, no amount, no condition, never a zero, an unavailable period, and a period
  whose data only partly covers its hours being reported as incomplete); the order of the payload
  never changing a result; time zones (the short night across the spring transition holding 7 hourly
  points, the long night across the autumn transition holding 9 with both occurrences of the repeated
  02:00, a half-hour zone at +05:30, a quarter-hour zone at +05:45, and the location zone being used
  rather than the device zone); the representative condition (dominant group, related conditions
  adding up, heavy hours outweighing a larger number of quiet hours, a brief thunderstorm staying
  visible as a separate hint while the period itself reads "Bedeckt", a mostly stormy period
  representing the storm, the strongest hail being named, the day/night variant following the
  majority of the period's own hours, and `UNKNOWN` carrying no vote); and the data source (a
  complete 96-hour series yielding 6/6/4/8 hours for the four periods while the rolling 24-hour
  subset yields nothing for the day after tomorrow, and `ForecastSections.allHours` carrying all 72
  hours of the response while the strip shows 24).
- **Theme, navigation, information content and backup configuration** (35 tests):
  `ThemeModeTest` (8) — the default is `SYSTEM`, every stored name round-trips, an unknown, blank
  or malformed value falls back to `SYSTEM`, matching is trimmed and case-insensitive, the stored
  form is the enum name, `SYSTEM` follows the system while `LIGHT` and `DARK` ignore it, and the
  DataStore decoder is checked directly;
  `AppearanceViewModelTest` (7) — `SYSTEM` on a fresh store, a stored mode is restored on start,
  a selection is applied **before** the write finishes and then stored, every option is stored as
  itself, a failing write keeps the choice and reports it, the warning is cleared by the next
  selection, and a theme change never writes the location settings nor triggers a forecast request
  (both stores wired to a real `MainViewModel`);
  `AppBackStackTest` (12) — the dashboard is the root and offers no back action, dashboard ↔
  location management, dashboard ↔ settings, settings ↔ appearance, settings ↔ about, licence
  detail returns to about and not to settings, the **privacy page** returns to settings and not to
  about (and its stored names round-trip), appearance/privacy/about being siblings below settings,
  popping the dashboard is a no-op that can never
  empty the stack, the stack round-trips through its stored names, and unknown or truncated stored
  names come back as the dashboard instead of crashing;
  `AboutContentTest` (6) — licence ids are unique and resolvable, every entry carries resource
  backed text (nothing hard-coded), every link is an https URL with a host or deliberately empty,
  the forecast API, the geocoding API and the GeoNames database are named together with the data
  licences, the Apache component list mirrors the shipped artifacts and excludes protobuf, and the
  BSD-licensed protobuf runtime inside DataStore plus the project's own artwork are listed
  separately;
  `BackupManifestTest` (3) — the manifest enables backup and references **both** rule files, no
  cleartext traffic is permitted, and `INTERNET` is the only permission the application declares
  (the privacy page states that the position of the device is never asked for, and the manifest has
  to stay in line with that sentence).
- **Instrumented tests** (`app/src/androidTest`): Room cache store round-trip, replace,
  delete, listing every stored key and removing a batch of them — an empty batch has to delete
  nothing instead of reaching the database as an empty `IN ()` — and the same two steps through a
  real `WeatherRepository`, so the reconciliation queries run against Room itself and not only
  against the in-memory double (in-memory database); Compose UI tests for the rendered dashboard, the hourly strip
  starting at the current hour with its day label, the hourly detail sheet, the "no usable
  observation time" label, the error state with its retry action, cached data being presented
  under the "Zuletzt aktualisiert" line **without** any status strip below it, and the toolbar
  opening the **full screen location manager**
  (`LocationManagerScreenTest`, 34 tests): the manager shows its own title, the fixed search
  field and the saved places; the three sections **Hauptstandort / Favoriten / Verlauf** are
  separate and always in that order (their measured tops are compared), the home entry is marked
  by a house, carries no star and **offers no action of its own** — it has no overflow button, and
  no "Hauptstandort aufheben" entry appears anywhere, before or after opening the menu of the row
  above it —, an empty history says so and offers no "Alle löschen" action,
  and history entries are listed newest first; the field and title stay in place while a long
  list is scrolled and the home entry scrolls out with the list only; the last entry can be
  scrolled near the bottom edge without any overlay and every row action keeps at least a 48 dp
  touch target (bounds compared, no fixed pixel values); suggestions overlay the list without
  moving it, carry **no save button at all**, and tapping one hands the place to the dashboard and
  leaves the screen; tapping a favourite row or a history row does the same, while the star, the
  delete icon and the overflow button of a row act on that row only and never navigate; the star
  of a history entry saves it, deleting one entry leaves the others on screen, and "Alle löschen"
  first shows the confirmation dialog — cancelling keeps everything, confirming clears, and a back
  press closes the dialog without leaving the screen; the first "up" and the last "down" menu
  entry report `Disabled` through semantics while the opposite end does not; a long place name
  never overlaps an action button and no button is cut off at the screen edge; and driving a
  **real `LocationRepository`** (with an in-memory `SettingsStore`) through the UI promotes a
  favourite to home, puts the previous home back into the favourites at the slot the new home left,
  keeps every place on screen exactly once and stores the new home as the selected location, saves
  a history entry into the favourites without listing it twice, and deletes one history entry while
  favourites, home location and selection stay untouched.
- **Navigation through the real root composable** (`WeatherRootNavigationTest`, 17 tests): the
  dashboard toolbar carries the application name; settings, location management, appearance, about,
  a licence detail and the **privacy page** each open as their own screen and return to the right
  parent through the header arrow; the licence detail returns to about and not to settings, while the
  **privacy page returns to settings** and not to about; the settings screen lists its three entries
  in the order **Darstellung, Datenschutz, Infos** (their measured positions are compared, not just
  their presence); the information page has **no** privacy entry anymore; the location manager has
  **exactly one** back arrow, so the floating button is gone; the **system back button** walks the
  same hierarchy (appearance → settings → dashboard, licence → about → settings → dashboard and
  privacy → settings → dashboard);
  and opening settings, appearance, the location manager or the privacy page and coming back sends
  **no** forecast request, writes the location settings **no** extra time, and keeps the selected
  location and the favourites untouched.
- **Daily detail sheet on the dashboard** (`DashboardScreenTest`, 24 tests, and
  `DayForecastSheetNavigationTest`, 3 tests): the toolbar title is measured **at least as tall** as
  the title of a secondary screen header in the same composition, stays clear of the three action
  buttons (bounds compared) and remains usable with a doubled font scale; both detailed day cards
  offer the "Details" action; tapping "Morgen" opens a sheet whose header names that date and shows
  the four period labels with their printed ranges ("06–12 Uhr" … "22–06 Uhr"), tapping "Übermorgen"
  opens the sheet for the day after tomorrow; a day without hourly data shows the unavailable notice
  in all four periods instead of values; the close button and the **system back key** both leave the
  sheet and keep the dashboard usable; the hourly "Stundendetails" sheet still works next to the new
  one; and driving the **real `MainViewModel`** with a counting fake API, both cards open their sheet
  with four populated periods **without a single additional forecast request** (the call counter is
  compared before and after opening, scrolling and closing). The hero card of a place whose weather
  has not arrived yet **names that place**, shows its star and the loading line with a progress
  indicator, and prints **no** temperature, condition or hourly strip — nothing is invented; a
  failed load keeps the name on screen, shows the existing error message and offers
  "Erneut versuchen", and the card does not fall back to another place.
- **The favorite star of the hero card** (`WeatherPresentationUiTest`, 14 tests, plus three star
  tests in `DashboardScreenTest`): the star says what the displayed place is — "Als Favorit
  hinzufügen" while it is not saved, "Aus Favoriten entfernen" once it is, and the home location's
  own label instead of an offer to remove it; it is a button of its own (a tap reaches the
  callback), it shares the line of the location name rather than the weather symbol next to it, and
  its touch target is at least 48 dp; on the dashboard the star follows the role of the displayed
  location, so a favourite offers removing and a merely viewed place offers saving.
- **Privacy page** (`PrivacyScreenTest`, 9 tests): the header shows the German title and the back
  action, all seven topics have their own heading, the back arrow leaves the page, the link at the
  end of a long scrolling page is reachable by scrolling, the whole notice is present without any
  network call, the page renders in the light **and** the dark scheme, and the wording is checked
  against the implementation: the two hosts and their endpoints are named, the visible IP address
  and the entered search term are mentioned, cookies / advertising / analytics / telemetry are
  denied, the stored values are listed, the provider notice links to the official Open-Meteo terms
  and privacy page, and the backup section names the same exclusions the xml rules declare.
- **Backup configuration against the real file names** (`BackupConfigurationTest`, 4 tests): both
  rule resources are parsed the way the platform parses them; the legacy file excludes the cache
  database and nothing else; the Android 12 file configures `cloud-backup` and `device-transfer`
  identically; the excluded names are compared with `getDatabasePath(...)` of the database the
  application actually opens (including `-wal` and `-shm`); and **both** DataStore files — the one with
  the saved places and the one with the widget configurations — are computed from the same API the app
  uses and checked against every rule, so no rule may hide what the user configured.
- **Appearance page** (`AppearanceScreenTest`, 8 tests): the shared header with the back arrow, all
  three options offered, exactly the selected mode marked (and only one), `SYSTEM` marked when
  nothing was chosen, tapping a row reports exactly that mode, a failed write is reported on the
  page and no warning appears otherwise, and the stored mode — not the system setting — decides the
  colour scheme: a pinned light scheme equals a light system and differs from a pinned dark one.
- **Information page** (`AboutScreenTest`, 8 tests): the installed package really is labelled
  **WetterPlus** (the launcher label and the string resource agree), the version and the build
  number on screen are read from `PackageInfo` rather than written into the layout, the weather
  source section and the licence section are present, the model wording states that the model
  depends on the location and names `best_match`, every licence row is listed and opens its own
  page, the **Datenschutz entry is no longer present** (it lives on the settings screen, and the
  test asserts that the information page does not offer it a second time), and a licence page carries
  author, licence identifier, summary and the offline note without
  a network — the library page additionally lists the components it covers.
- **Theme preference against a real DataStore file** (`ThemeStoreTest`, 5 tests): `SYSTEM` for a
  fresh file, the chosen mode written to the preferences and read back by a store created
  afterwards, every mode stored as itself, a hand-written invalid value falling back to `SYSTEM`,
  and a theme write leaving the saved locations in the same file identical.
- **Location settings against a real DataStore file** (`SettingsStoreTest`, 6 tests): a fresh file
  holds no locations and no history; home, favourites, history and the selection survive a store
  created afterwards, with both orders intact; **a preferences file written by an older build that
  never had a `recent_locations` key still loads completely** — the home location, the favourites
  in their order and a valid selection are kept and the history simply reads as empty; a damaged
  list value becomes empty without taking the rest of the file down with it; a selection
  pointing at a place that is not stored any more is repaired to the home location; and a legacy
  file that stores the same place twice is read as one consistent state — the role with the higher
  precedence keeps it, the history loses the duplicate, and the selection stays valid.
- **Toolbar and header touch targets**: the three dashboard actions and the header back arrow are
  measured in the semantics tree and must be at least 48 dp on both axes, aligned on one row and
  without overlapping.
- **Presentation layout and icon family tests** (`WeatherPresentationUiTest`, 14 tests, two of
  which are the favorite star described above):
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
- **The rolling day periods** (`RollingDayPeriodsTest`, 17 tests): the slot sequence always runs
  Vormittag → Nachmittag → Abend → Nacht and never skips one; a window read at 03:00 starts with the
  night that began **the day before**, a window read late at night wraps into the following day; a
  boundary hour belongs to the period that starts at it; the night is never cut in two at midnight;
  the slot follows the location's zone rather than one shared absolute offset; a zone with a
  fractional offset is cut at *its own* wall-clock hours; the autumn transition names the repeated
  hour by the night it belongs to and the spring transition keeps night and morning adjacent with no
  gap; a period beyond the forecast keeps its slot and reports that it has no data; hours given in
  any order aggregate identically; and every period is produced by **the same code** the daily detail
  sheet uses, with the probability staying the highest hourly value.
- **Widget content** (`CurrentWidgetContentTest`, `PeriodWidgetContentTest`,
  `ThreeDayWidgetContentTest`): "heute / morgen / übermorgen" are the **calendar days of
  the location**, not of the device, a daily row is matched by its **date** and not by its position in
  the response, and the rows roll over at midnight *of the location*; an hourly value counts as
  "current" only when its interval contains the reference instant, and the current-weather widget's
  probability is the **current hour's** value. Compact and expanded layouts carry up to two and four
  metric values respectively, and a value the response did not report is
  absent rather than a placeholder; a missing location, an empty snapshot and a day without hourly or
  daily rows each get their own state instead of a guess; a period card names its local time range and
  the weekday of the day the period belongs to, and a period the forecast does not reach keeps its card
  and says so; every weekday, date, range and amount is produced by the app's own German formatters.
- **Sizes and previews** (`WidgetSizesTest`, `WidgetProviderMetadataTest`): tests cover the
  width breakpoint, height-independent layout selection, cell targets, horizontal resizing and
  inflation of each dedicated preview.
- **Widget configurations** (`WidgetConfigRepositoryTest`, 19 tests): configurations are keyed by the
  widget id and saving the same id again replaces rather than appends; deleting one widget leaves the
  others alone and deleting the last empties the store; pruning keeps exactly the ids the launcher still
  holds and a deleted widget stops protecting its place; a **pinned** widget keeps its place even after
  that place left the favourites and the history, and its id is still collected for cache retention; a
  pinned widget whose location record is missing shows nothing rather than a random place; a
  Hauptstandort widget follows the app and shows nothing while the app has no home location; an
  unconfigured widget behaves like the home location; two widgets of the same type can show two
  different places and two widgets of different types keep separate records.
- **The widget data path** (`WidgetDataProviderTest`): a fresh cache is served without any
  network call, a presentation refresh reads the cache only, a forced refresh ignores the age, an
  outdated cache is refreshed and the new values are shown, and a failed refresh keeps the cached values
  visible; **two widgets showing the same place cause exactly one request** while two different places
  load independently; a pinned place that left the app is still loaded, a place that was never fetched
  reports no data instead of a guess; the set of displayed places is deduplicated; and every content
  builder receives the same reference instant that the data was loaded with.
- **Widget configuration screen, file and schedule on a device**: the configuration screen
  (`WidgetConfigurationScreenTest`) offers all three modes with the sentence
  that explains them, marks exactly the current one, asks a pinned widget for a place, names a stored
  place once it is picked, explains a Hauptstandort widget while the app has no home location, reports a
  write that did not reach storage, and closes without writing anything on cancel; the configuration
  file (`WidgetConfigStoreTest`, 12) is exercised against a real DataStore — a fresh file holds nothing,
  every mode survives a store created afterwards, a pinned place is stored complete including its time
  zone, a damaged value reads as "no widgets", an entry with an unknown mode is skipped without taking
  the rest down, and removing an unknown id changes nothing; the schedule
  (`WidgetRefreshSchedulingTest`, 7) runs against the **device's real WorkManager**: several widgets
  share a one-time refresh, repeated requests do not stack up, exactly one periodic job is
  scheduled for all of them, that job disappears when the last widget is gone and comes back when one is
  configured, no widgets mean no work at all, each newly configured widget has its own initial
  refresh, and the periodic interval equals the cache lifetime.
- Six of the 46 `MainViewModelTest` tests belong to the widgets: the widgets are asked to redraw when the
  app has new data or another location, a place a widget is pinned to keeps its cached forecast although
  the app does not use it, deleting a location a widget still shows keeps that forecast, and the three
  tap paths — a home-location widget, the already displayed place, and a pinned place the app no longer
  knows, which is shown **without favouriting it** and without rewriting the stored home location.

Widget stabilization tests cover loading-to-success delivery into an active composition, offline
fallback, retry, and refreshes arriving after reconfiguration or deletion
(`WidgetDataProviderTest`). `WidgetConfigurationViewModelTest` checks reactive location
validation, search on first use, configuration persistence before scheduling, and cancellation.
Instrumented UI tests also verify that actions remain visible while results scroll.

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

### Privacy page, storage layout and backup rules (current iteration)

Same emulator (`Medium_Phone_API_37.0`, API 37, 1080×2400), real API traffic, screenshots
`app/build/verify/n20…n36*.png`.

- **Entry** (`n21_settings.png`, `n23_about_privacy_entry.png`): the information page ends with the
  section "Datenschutz" and the row "Datenschutz bei WetterPlus / Welche Daten die App speichert
  und überträgt" with the shield icon and a chevron; tapping it opens a full screen, not a dialog.
  *(That entry has since moved to the settings screen — see the current iteration below; the same
  screen and the same back behaviour were re-verified there.)*
- **Privacy page, light** (`n24_privacy_top.png`, `n25_privacy_middle.png`,
  `n26_privacy_backup.png`, `n27_privacy_bottom.png`): "← Datenschutz" at the top left, then the
  intro and the seven sections; the stored values, the transmitted values and the two endpoints are
  set off in cards, the backup section carries the "Vorgesehen für die Sicherung" and "Von der
  Sicherung ausgeschlossen" cards, and the last elements are the link button
  "Datenschutz- und Nutzungsbedingungen von Open-Meteo öffnen" and the offline note. The page
  scrolls to its end without any element being cut off.
- **Link** (`n28_privacy_link_browser.png`): the button opens Chrome on
  `open-meteo.com/en/terms`; coming back returns to the app on the page it was on, and one back
  press lands on "Infos".
- **Dark scheme** (`n29_appearance_dark.png`, `n31_privacy_dark_top.png`,
  `n32_privacy_dark_requests.png`): the same content on the dark surface, list marks in the primary
  colour, cards and text still separated.
- **Back behaviour**: arrow on the privacy page → "Infos"; arrow → "Einstellungen"; system back →
  dashboard. The dashboard afterwards still showed Hamburg with its forecast. *(The privacy page now
  sits one level higher: arrow → "Einstellungen", system back → dashboard, re-verified below.)*
- **Storage as it really is** (`run-as com.example.weatherapp ls`):
  `files/datastore/weather-settings.preferences_pb` (467 bytes with two saved places),
  `databases/weather-cache`, `weather-cache-wal`, `weather-cache-shm`, plus `cache/` and
  `code_cache/`. The database file has **no** `.db` suffix, which is what the rules name.
- **Backup pass executed on the device**, not just reviewed: with `BackupXmlParserLogging` at
  verbose level, `bmgr backupnow com.example.weatherapp` logged `Found valid
  data-extraction-rules`, the excludes `databases/weather-cache`, `-wal`, `-shm` (and the
  auto-generated `-journal` variants) and `files/icon_gallery`, and `Includes: …nothing specified
  (This means the entirety of app data minus excludes)`. `FullBackup_native` walked
  `files/datastore/weather-settings.preferences_pb` and measured the `databases` directory at 0
  bytes, i.e. the cache was skipped.
- **Restore cycle**: back up, `pm clear`, uninstall, reinstall, first launch. Android restored the
  preferences file (same 467 bytes, original modification time), the app came up with Hamburg as
  home and Berlin as favourite, and the weather was fetched again; the cache database was created
  fresh rather than restored. Afterwards the emulator's backup settings were put back to what they
  were (transport `com.google.android.gms/.backup.BackupTransportService`, backup disabled).
- Logcat for the whole session: empty crash buffer, no `FATAL EXCEPTION`, no warning or error
  logged by the application process.

### Settings entry for Datenschutz, toolbar title and daily detail sheets (current iteration)

Verified on the `Medium_Phone_API_37.0` emulator (1080 × 2400) with the debug APK installed through
`installDebug`; screenshots were taken at each step and inspected.

- **First start and live data**: the setup screen offers the search field, typing "Hamburg" brings up
  the live geocoding suggestions, and choosing "Hamburg / Freie und Hansestadt Hamburg, Deutschland"
  loads a real forecast (2 °C, Bedeckt, wind 12 km/h, gusts 27 km/h).
- **Toolbar title**: "WetterPlus" renders in `headlineSmall` and is visibly larger than the section
  headers below it ("Nächste 24 Stunden", "Nächste zwei Tage") and than the header of any secondary
  screen. With the system font scale set to 1.5 the title stays on one line inside the toolbar and
  clear of the three action buttons; the scale was set back to 1.0 afterwards.
- **Settings navigation**: the settings screen shows exactly three rows in the order **Darstellung,
  Datenschutz, Infos**, the middle one with the shield symbol and the summary "Welche Daten die App
  speichert und überträgt". Tapping it opens the existing privacy screen with its own header
  "Datenschutz"; the header arrow returns to "Einstellungen". The information page ends with the
  licence list ("Wetter- und App-Symbole / Eigene Erstellung") and contains **no** privacy entry.
- **Daily detail sheet, "Morgen"**: the card carries the "Details ›" affordance, and tapping it opens
  a bottom sheet headed "Tagesverlauf · Donnerstag, 26. November" with the four periods
  **06–12 Uhr, 12–18 Uhr, 18–22 Uhr, 22–06 Uhr**, each with its symbol, condition, temperature range
  and the four aggregated values ("Max. Regenw. 0 %", "Niederschlag 0 mm", "Wind im Mittel 5 km/h",
  "Böen bis 13 km/h"), followed by the footnote explaining the three rules.
- **Daily detail sheet, "Übermorgen"**: the second card opens the sheet for "Freitag, 27. November"
  with the same four periods, aggregated from the hourly series of that day — the day after tomorrow
  is as complete as tomorrow.
- **Night across midnight**: the "Nacht" period of both days is printed as 22–06 Uhr and holds the
  early hours of the following calendar day, which the printed range makes visible.
- **Day/night symbol follows the period's own hours**: the Vormittag of 26 November shows the moon
  with "Klar", because three of its four clear hours (06:00–08:00) lie before sunrise — checked
  against the `is_day` values the API itself returns for those hours.
- **Dismiss and back hierarchy**: the close button and the **system back key** each leave the sheet;
  afterwards the dashboard still shows Hamburg with its forecast, and the hourly "Stundendetails"
  sheet still opens next to the new one.
- **Offline**: with Wi-Fi and mobile data switched off, the dashboard shows the cached report under
  "Zuletzt aktualisiert: vor 14 Minuten" instead of an error, and the daily sheet still renders all
  four periods from the cached hourly series.
- **Large text**: at a system font scale of 1.5 the four values of a period are laid out as two rows
  of two, all four periods and the footnote stay reachable by scrolling, and no value is cut off.
- **Light and dark**: both schemes captured for the dashboard and for the sheet; the theme was set
  back to "Systemeinstellung" at the end of the session.
- **Logcat** for the session: no `FATAL EXCEPTION`, no warning or error logged by the application
  process (only the emulator's own GPU/CPU-variant notices).

Two defects were found by this on-device pass and fixed: a period whose hours all reported 0 mm
rendered as "–" because a reported zero had been filtered out as if it were missing, and a
temperature range below zero rendered as "-2–-1 °C", which now prints as "-2 – -1 °C".

Not verified on the device in this iteration: that opening a sheet sends **no** forecast request was
proven by the instrumented test that counts the calls of a fake API
(`DayForecastSheetNavigationTest`), not by inspecting the emulator's network traffic; the offline
run above only shows that the sheet needs no request to render.

Not verified in this iteration: a real Google cloud backup and a physical device-to-device
transfer (both need an account and a second device) — the `<device-transfer>` section was reviewed
and parsed by the platform, but no actual phone-to-phone transfer was performed; and whether
Open-Meteo logs anything beyond what its own terms and privacy page states, which is outside this
application.

### Location roles, the favorite star and the history (1.1.0)

Verified on the `Medium_Phone_API_37.0` emulator (1080 × 2400) with the debug APK installed over the
existing installation through `installDebug` — no app data was cleared. Every step was screenshotted
and inspected (`app/build/verification/01…17.png`), and the UI hierarchy was dumped with
`uiautomator` to read the exact labels and the bounds of each control.

- **Setup and live data**: the setup screen offers the search field, typing "Hamburg" brings up the
  live geocoding suggestions, and choosing "Hamburg / Freie und Hansestadt Hamburg, Deutschland"
  loads a real forecast (0 °C, Weitgehend klar, Min 0 / Max 2 °C). The star on the card is **filled
  in warm gold**, because that place is the home location.
- **Three sections, in the required order**: the manager shows **Hauptstandort** (house symbol,
  "Wird gerade angezeigt", and only a three-dot menu — no star), **Favoriten** with "Noch keine
  Favoriten. Tippe den Stern an, um einen Ort zu speichern.", and **Verlauf** with "Noch kein
  Verlauf. Orte, die du nur anschaust, erscheinen hier." With an empty history there is **no**
  "Alle löschen" action.
- **A suggestion is only viewed, never saved**: searching "Neustadt" lists suggestions that carry
  **no save button at all**, and tapping "Neustadt (Eichsfeld)" left the screen at once. The
  dashboard then showed **that place's own data** (-2 °C, Bedeckt, Min -5 / Max 0 °C, wind 17 km/h,
  gusts 30 km/h) rather than Hamburg's, and the star on the card was **outlined** with the
  accessibility label "Als Favorit hinzufügen".
- **The star saves**: one tap filled it in gold and printed "Zu Favoriten hinzugefügt."; the weather
  on screen did not change, so no second request was triggered. In the manager the place now sat
  under **Favoriten** with a filled star and its own menu, while the **Verlauf stayed empty** —
  saving takes a place out of the history.
- **Compact overflow menu**: the favourite's menu holds "Als Hauptstandort setzen", "Nach oben
  verschieben" and "Nach unten verschieben"; with a single favourite the two move entries render
  greyed out.
- **Home swap keeps everything**: "Als Hauptstandort setzen" moved Neustadt into the Hauptstandort
  section (house symbol, "Wird gerade angezeigt") and put Hamburg into Favoriten with its star,
  under the message "Hauptstandort aktualisiert."
- **Un-favouriting moves into the history**: the star of the Hamburg row printed "Aus Favoriten
  entfernt – der Ort bleibt im Verlauf." and the row reappeared as the first entry of the Verlauf
  with three actions (outlined star, delete, menu); the home location and the displayed place were
  untouched.
- **History order is newest first**: after viewing "Wejherowo" through a suggestion, the Verlauf
  listed Wejherowo (marked "Wird gerade angezeigt") **above** Hamburg.
- **One entry can be deleted alone**: the delete icon of the Hamburg row removed only Hamburg and
  left Wejherowo in place.
- **Bulk delete asks first**: "Alle löschen" opened a dialog titled "Verlauf löschen?" with the text
  "Möchtest Du alle Orte aus dem Verlauf entfernen? Favoriten und Hauptstandort bleiben erhalten."
  and the actions Abbrechen / Löschen. **The system back key closed the dialog** and left the screen
  with its history intact. Confirming with "Löschen" emptied the section, removed the "Alle löschen"
  action, printed "Verlauf gelöscht.", and the display fell back to the home location, which the
  manager then marked "Wird gerade angezeigt".
- **The home location cannot be lost by accident**: its star reads "Hauptstandort: dieser Ort ist
  immer gespeichert" and tapping it only prints "Der Hauptstandort ist immer gespeichert."; its menu
  holds the single destructive entry "Hauptstandort aufheben", drawn in the error colour. Removing it
  while no favourites existed returned the app to the setup screen, as before. *(That entry was
  removed in 1.1.1 — see the next subsection — because it was the one action that could leave the app
  without a home location.)*
- **Persistence**: after `am force-stop` and a cold start the app opened on the dashboard with the
  same home location and its forecast; nothing had to be entered again.
- **Logcat** for the session: no `FATAL EXCEPTION`, and no warning or error logged by the
  application process — the only `AndroidRuntime` lines come from the `uiautomator` dump itself.

Not verified on the device in this iteration: the **30-entry cap and its eviction** were exercised by
the repository and ViewModel unit tests, not by browsing 31 places on the emulator; and that saving
or un-saving a place triggers **no** forecast request is proven by the ViewModel tests that count the
calls of a fake API, not by inspecting the emulator's network traffic. The stored data on this
emulator happened to be empty when the new build was installed (an earlier iteration's backup test
had wiped it), so reading a **populated** pre-history preferences file on a real device was not
exercised here — that path is covered by the instrumented `SettingsStoreTest`, which loads a
preferences file written without the `recent_locations` key.

### Stabilization of the location workflow (current iteration)

Verified on the emulator `Medium_Phone_API_37.0` (Android 17, 1080×2400) with the 1.1.1 debug APK
installed over the previous build (`adb install -r`, versionCode 3 → 4, no data reset by the
install itself). Screenshots: `app/build/verification/`.

- **Version**: the Infos screen reads "Version 1.1.1" and "Build 4"; `dumpsys package` reports
  `versionCode=4` / `versionName=1.1.1`.
- **Start**: the instrumented test run had removed the app, so the first launch showed
  "Standort einrichten"; searching "Hamburg" and tapping the first suggestion created the home
  location and opened the dashboard with live data.
- **The home row offers no action**: with only a home location the manager contains **zero**
  overflow buttons, and the string "Hauptstandort aufheben" appears nowhere in the semantics tree —
  neither on the row nor after opening the menu of the row above it. Tapping the home row still
  selects it and returns to the dashboard, so the place did not become inaccessible.
- **Home swap keeps everything**: the favourite's menu holds only "Als Hauptstandort setzen",
  "Nach oben verschieben" and "Nach unten verschieben". Choosing it moved Kyōto into Hauptstandort
  and put the previous home Hamburg into Favoriten as a normal favourite, under the message
  "Hauptstandort aktualisiert."
- **The home star stays protected**: tapping it prints "Der Hauptstandort ist immer gespeichert."
  and the star keeps its home label.
- **A place is named before its weather arrives**: with the cache database deleted
  (`run-as … rm databases/weather-cache*`) and the network pointed at an unreachable proxy
  (`settings put global http_proxy 10.255.255.1:8080`), selecting Kyōto showed a card carrying
  "Kyōto", "Präfektur Kyōto, Japan", the outlined star and "Wetterdaten werden geladen …" with a
  small progress ring — **no** temperature, **no** condition, **no** hourly strip, and nothing left
  over from the previously displayed place.
- **A failed load keeps the place**: after the connect timeout the same card still named Kyōto,
  showed "Der Wetterdienst antwortet nicht schnell genug. Bitte erneut versuchen." and
  "Erneut versuchen"; the app did not navigate back and did not revert to another place. The retry
  returned to the loading card, and with the proxy removed the next retry loaded live data.
- **Rapid star taps are two changes**: two `input tap` calls in immediate succession on the card
  star produced two state transitions — add then remove, and remove then add — with the star ending
  in the state opposite to where it started; a single tap prints "Zu Favoriten hinzugefügt."
- **The history cap and its cache rows**: 31 distinct places were opened one after another through
  the search. The manager then listed exactly **30** history entries, newest first, without the two
  oldest (Tokio and Paris). The cache database was pulled with `run-as … base64` and read with the
  SDK's `sqlite3`: it holds exactly **32 rows** — the 30 listed history entries plus the home
  location and the single favourite. Since the database was recreated at the start of this run, 33
  distinct places had been fetched (home, favourite and the 31 opened places), so the row of the
  evicted Paris entry had been removed rather than left behind; the other evicted entry, Tokio, had
  been fetched *before* the deliberate database deletion, so its absence proves nothing on its own.
  After `am force-stop` and a cold start the count was still 32: the reconciliation at start removes
  nothing that is still referenced.
- **Sections and roles**: starring the Budapest history entry moved it from Verlauf to Favoriten;
  un-starring put it back at the top of the Verlauf. A history row's menu holds only "Als
  Hauptstandort setzen"; its delete icon removes that one entry, while "Alle löschen" opens the
  "Verlauf löschen?" dialog — Abbrechen keeps everything.
- **Light and dark**: the Hell/Dunkel choice re-skins every screen immediately; the dashboard and
  the location manager were captured in both schemes.
- **Logcat** for the session: no `FATAL EXCEPTION`, no warning or error logged by the application
  process, and no SQLite or Room complaint.

Not verified on the device in this iteration: the **legacy settings recovery** — a preferences file
that holds places but no home location, or the same place in two roles — is covered by the
instrumented `SettingsStoreTest`, which writes such a file directly; producing it on the emulator
would have meant rewriting the stored settings, which this iteration must not do. And the
**atomicity of the star under real touch latency** is proven by the repository test that runs two
`toggleFavorite` calls concurrently against the same lock; on the device the taps were delivered as
two shell commands, which is close but not the same as a finger.

### Widget stabilization (1.2.1)

On the API 37 Pixel Launcher, the first 1.2.0 current-weather widget stayed on its loading
message. After upgrading to 1.2.1, a first fixed-location widget for Springfield resolved the
live forecast before any other widget was added. The home-following rolling widget and
selection-following three-day widget initialized independently. The three-day widget was
reconfigured to Dresden through the launcher, then rendered its cached forecast after the APK
was reinstalled in airplane mode. All three previews and each widget at both widths were
inspected, along with the configuration footer above the keyboard and the full-height daily rows.
An isolated Android user with fresh app storage also blocked both unset follow modes and
fetched live weather for Oslo in the first and only widget. Switching it to an uncached location
in airplane mode displayed an unavailable message. Restoring connectivity and opening the app
refreshed the same widget. The footer remained visible at 150% font scale, and cancelling a
placement left the existing widget intact. Removing the existing widget deleted it from the
launcher. The full verification passed 435 unit tests and 175 instrumented tests.
Screenshots are in `app/build/verify/widgets-1.2.1/`.

### Home-screen widgets (1.2.0)

Walkthrough on the `Medium_Phone_API_37.0` emulator (SDK 37, Pixel launcher `com.google.android.apps.nexuslauncher`,
1080 × 2400 px at density 2.625 → 85 dp launcher cells) with the freshly installed `app-debug.apk`
(`dumpsys package` → `versionCode=5`, `versionName=1.2.0`). Screenshots of the configuration screen and of
the two larger layouts are under `app/build/verify/` (`widget-01-configure.png`, `widget-12-medium.png`,
`widget-14-expanded.png`); the other observations below were read from `uiautomator` dumps and `dumpsys`
output rather than photographed, and stored state was read back with
`run-as com.example.weatherapp cat files/datastore/…`.

- **The picker**: all three providers are listed with their German names and descriptions, each showing
  the declared **3 × 2** default span; `dumpsys appwidget` reports `updatePeriodMillis=0`,
  `resizeMode=3` and three providers of this package.
- **Configuration on add**: every add opens "Widget einrichten" with the three modes. On a fresh install
  without a home location, the Hauptstandort mode shows "Dieses Widget zeigt den Hauptstandort. Lege
  zuerst in WetterPlus einen Hauptstandort fest." and is still savable; **Abbrechen** leaves no widget
  on the home screen (the activity answered `RESULT_CANCELED`).
- **The first draw of a newly configured instance belongs to the launcher, not to the app**: a
  3-Tage-Vorschau pinned to Newark was stored in the same second as the "Speichern" tap —
  `{"widgetId":38,"mode":"FIXED","fixedLocation":{ …Newark, America/New_York }}` — and
  `files/datastore/appWidgetLayout-38` showed that Glance knew that very id. Yet the cell kept showing
  "Wetterdaten werden geladen …", and `uiautomator` reported its node as **not clickable**, which is how
  it became clear that the composition Glance had run for it carried no resolvable App Widget id. The same
  instance drew Newark properly — place name, "Zuletzt aktualisiert", three rows — as soon as the app was
  updated and the launcher re-bound the widget. `onWidgetConfigured` (periodic job + unique one-time
  refresh + the redraw of that single instance) is implemented and asserted by the instrumented
  scheduling test, but it could not be shown to shorten that first composition on this launcher; what it
  did achieve is that the cell says "loading" instead of claiming "Kein Ort eingestellt" without basis.
- **One record per instance**: with three widgets placed, `files/datastore/widget-configurations.preferences_pb`
  held `[{"widgetId":29,"mode":"SELECTED"},{"widgetId":30,"mode":"HOME"},{"widgetId":32,"mode":"FIXED",
  "fixedLocation":{ … "name":"Newark", "timeZoneId":"America/New_York"}}]` — three ids, three modes, and the
  pinned place stored with coordinates and time zone. Glance's own per-widget files
  (`files/datastore/appWidgetLayout-29/-30/-32`) confirm the same ids.
- **Deleting cleans up**: after removing two instances the same file contained only
  `[{"widgetId":29,"mode":"SELECTED"}]` — checked twice, with different instances removed each time.
- **Three modes side by side**: the HOME and the SELECTED widget showed the same Hamburg values, while a
  SELECTED current-weather widget kept showing Hamburg after a FIXED widget had been pinned to another
  continent — the widgets do not follow each other.
- **The widget follows the app without being re-added**: on the fresh install the HOME-mode widget read
  "Kein Ort eingestellt…"; once the onboarding search had created Hamburg as the home location, the same
  widget switched to Hamburg data on the next refresh.
- **Rolling periods in the widget location's time zone**: the Tagesverlauf widget pinned to Newark was
  read at 09:20 Berlin time, i.e. **03:20 local time in New Jersey**, and rendered **Nacht 22–06 Uhr,
  Vormittag 06–12 Uhr, Nachmittag 12–18 Uhr, Abend 18–22 Uhr**. The sequence starts with the period that
  contains the *place's* local time, not the phone's, and the night card is captioned
  "Do. · 22–06 Uhr" because that night belongs to the day it starts in — which is not "Heute" there.
- **Every layout was rendered**: compact 2 × 2 for all three widgets; medium 3 × 2 (276 × 210 dp) for the
  3-day widget with its "54 % · 2,9 mm" column and for the current-weather widget with exactly four tiles
  (Heute min/max, Luftfeuchtigkeit, Regenw. of the **current hour**, Niederschlag); expanded 4 × 3
  (373 × 321 dp) for the current-weather widget, where the full 3 × 3 grid appeared with all nine metrics,
  ending in "Luftdruck 1.027 hPa · Bewölkung 78 % · Windrichtung NO (42°)". The widget numbers matched the
  dashboard's numbers at the same minute.
- **Resizing**: the right edge grew the widget from 3 × 2 to 4 × 2; the bottom edge grew it from two to
  three rows once the cells below were free — the launcher refuses to grow a widget into a row that
  already holds something, which is why the resize cap has to sit above three cell rows (400 × 340 dp).
- **Taps land at the right place**: tapping the Newark widget opened WetterPlus showing Newark, with the
  header "Freitag, 27. November 2026, 03:15" — the place's own local time. The card's star still read
  "Als Favorit hinzufügen", and the settings file afterwards held `home_location` = Hamburg,
  `favorite_locations` = empty and `recent_locations` = [Newark]: the pinned place entered the
  **history**, never the favourites, and the home location did not move.
- **Offline**: with airplane mode on (`Active default network: none`) the dashboard kept its cached values
  under "Zuletzt aktualisiert: vor 30 Minuten" and both widgets kept their numbers and age lines. No
  crash, no blank widget, `pidof` still reported the app process.
- **Light and dark**: `cmd uimode night yes` recoloured all three widgets within seconds — dark surface,
  lighter tiles, near-white text, the lighter sky token for the place name, and the Flat Color symbols
  unchanged and still readable; `cmd uimode night no` restored the light palette.
- **One job, not one per widget**: `dumpsys jobscheduler` lists a single WorkManager job
  `#WidgetRefreshWorker#` for the package with `Minimum latency: +29m59s984ms`,
  `Backoff: policy=1 initial=+10m0s0ms` and a connectivity constraint, while three widgets of two
  different places were placed.
- **Logcat** for the session: no `FATAL EXCEPTION`, no `GlanceWeatherWidget` or `WidgetRefreshWorker`
  warning during the walkthrough (the one spurious `JobCancellationException` warning seen earlier came
  from a test cancelling the unique work and is now rethrown instead of logged).

Not verified on the device in this iteration: the periodic job was never **observed firing by itself over
several hours** — what was checked is that JobScheduler holds one periodic WorkManager job with a
30-minute period and a network constraint, that the instrumented scheduling test sees exactly one periodic
and one unique one-time request, and that a manually requested refresh redraws the widgets. The
**system-event receiver** is registered and compiled but was not exercised, because the emulator's date,
time or time zone were not changed under a live widget; the period-boundary behaviour it is meant to
correct is covered by `RollingDayPeriodsTest`. The **`ACTION_MY_PACKAGE_REPLACED` prune** ran only in the
instrumented `WidgetConfigRepositoryTest`, not through a real upgrade. And everything was checked on one
phone emulator with the Pixel launcher at the default font and display scale — no tablet, no third-party
launcher, no large accessibility scale. The **time between configuring a widget and its first correct draw**
was observed on that launcher (minutes, and immediately after an app update) but never measured as a bound;
what is guaranteed by the code is a composition within the 30-minute periodic interval.

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
  into tomorrow) as exact values, and for "Morgen" and "Übermorgen" as period aggregates; the
  compact outlook from day 3 onwards stays daily data only, because its rows offer no drill-down.
  The hourly series of a response covers the whole 16-day horizon, so extending the period sheets
  to later days would need no additional request — leaving it at the two detailed cards is a layout
  decision, not a data limit.
- The four period values are **derived** from the hourly data, not reported by the weather service:
  the probability is the highest hourly value rather than a combined "at some point" figure, the
  amount is a sum, the wind is a mean. The representative condition is a documented, deterministic
  heuristic (group frequency weighted by intensity, then the most frequent concrete condition, then
  the majority day/night state) — it summarises a period and does not claim that the whole period
  looked like that, which is why a brief thunderstorm or hail is named separately.
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
- The **history is a bounded, local list of places that were actually displayed** — at most 30,
  never synchronised, and never a log of search queries: a place that was only typed into the
  search field but never opened does not appear. Like a favourite, an entry keeps the name and
  region the geocoding service returned when it was created; if the service later labels the same
  id differently, the stored entry is not rewritten.
- A place can only be in one role at a time, which is what the user sees, but it also means the
  history cannot act as a second ordering: un-favouriting always puts a place at the **top** of the
  history, and favouriting a history entry puts it at the **end** of the favourites. There is no
  manual ordering of the history.
- The home location cannot be un-set from the location manager at all: its row carries no star, no
  delete icon and no menu, and the repository additionally refuses any delete that names the home
  location, so no ordinary location-management action can remove it. The star on the dashboard card
  only explains that the place is always saved. The home role moves through "Als Hauptstandort
  setzen" on another place, and the previous home becomes a normal favourite. A settings file that
  carries places but no home location — possible only from an older build — stays usable: those
  places remain selectable, the setup screen is not shown again, and the first "Als Hauptstandort
  setzen" repairs the role.
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
- WorkManager can defer widget refreshes. Cached weather remains visible during temporary failures.
- Tapping a widget of a **pinned** location can move that place into the app's **history**, exactly as
  tapping a search suggestion does. It never adds a favourite and never changes the home location, but
  the behaviour is worth knowing: a place that was deliberately removed from the app can reappear in
  the history after a widget tap.
- Reconfiguration and enforcement of resizing bounds depend on the launcher.
- Widgets carry no per-instance appearance settings (no transparent background, no accent choice, no
  "show this many tiles" switch), and tapping a widget always opens the dashboard: there is no deep
  link to a specific day or day period, and `requestPinAppWidget` (letting another app place a widget)
  is not implemented. The size of the content is chosen by the widget's measured size only, so an
  unusually large system font scale can make a compact layout tighter than intended — verified at the
  emulator's default scale, not at the largest one.
- Glance resolves colours when the widget content is composed, and the library's resource-based
  `ColorProvider(Int)` is restricted to the Glance libraries themselves, so the widgets build
  day/night pairs through the public `androidx.glance.color.ColorProvider(day, night)` from the same
  `ui/theme/Color.kt` tokens the app uses. Static picker and fallback XML layouts have
  matching `values` / `values-night` colours. A live widget applies the system appearance
  when it recomposes.
- Backup: the **cloud** path was exercised end to end on the emulator with the local debug
  transport (backup, wipe, reinstall, restore). The `<device-transfer>` section is configured the
  same way and is parsed by the platform, but no real phone-to-phone transfer was performed, and a
  real Google cloud backup would need an account. Whether a device's backup is switched on at all
  is a system setting this app cannot control.
- The privacy screen describes what this application does. What the weather service does with the
  requests it receives is quoted from Open-Meteo's own terms and privacy page and can change there
  without any change in this repository; the screen says so. No legal assessment is made anywhere
  in the app or in this document.
- Lint reports **no errors**. Warnings cover dependency updates, three pre-existing unused
  app strings, and nested weights in the static picker previews.
