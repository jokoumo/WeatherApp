package com.example.weatherapp.data.remote

import com.example.weatherapp.data.remote.dto.CurrentDto
import com.example.weatherapp.data.remote.dto.DailyDto
import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.data.remote.dto.HourlyDto
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.isSamePlaceAs
import com.example.weatherapp.testing.geocoderResult
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.sampleForecastDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Mapping from Open-Meteo DTOs to domain models, including incomplete payloads, unparsable
 * timestamps, cache round trips and geocoding results.
 */
class ForecastMapperTest {

    private val mapper = ForecastMapper()
    private val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")

    @Test
    fun `daily mapping retains real zero duration and drops a row without measurements`() {
        val days = mapper.toDaily(
            DailyDto(
                time = listOf("2026-11-22", "2026-11-23"),
                sunshineDuration = listOf(0.0, null),
                daylightDuration = listOf(0.0, null),
            ),
            ZoneId.of("Europe/Berlin"),
        )
        assertEquals(1, days.size)
        assertEquals(0L, days.single().sunshineSeconds)
        assertEquals(0L, days.single().daylightSeconds)
    }

    @Test
    fun `an overflowing API offset falls back to the location time zone`() {
        val days = mapper.toDaily(
            DailyDto(time = listOf("1795302000"), temperatureMin = listOf(0.0)),
            ZoneId.of("Europe/Berlin"),
            utcOffsetSeconds = 1L shl 32,
        )
        assertEquals(java.time.LocalDate.of(2026, 11, 22), days.single().date)
    }

    @Test
    fun `geocoding rejects coordinates outside their physical bounds`() {
        val locations = mapper.toLocations(
            listOf(
                geocoderResult(1, "Too far north", 91.0, 0.0),
                geocoderResult(2, "Too far west", 0.0, -181.0),
                geocoderResult(3, "Invalid", Double.NaN, 10.0),
                geocoderResult(4, "Valid", 0.0, 180.0),
            ),
        )
        assertEquals(listOf("4"), locations.map { it.id })
    }

    @Test
    fun `a complete payload maps to current hourly and daily domain models`() {
        val snapshot = mapper.toSnapshot(sampleForecastDto(), hamburg, fetchedAt)

        assertEquals(ZoneId.of("Europe/Berlin"), snapshot.timeZone)
        assertEquals(384, snapshot.hourly.size)
        assertEquals(16, snapshot.daily.size)

        val current = requireNotNull(snapshot.current)
        assertEquals(6.4, current.temperatureCelsius, 0.0001)
        assertEquals(81, current.relativeHumidityPercent)
        assertEquals(true, current.isDay)
        assertEquals(
            Instant.parse("2026-11-22T11:00:00Z"),
            current.instant,
        )

        // Timestamps arrive as Unix seconds; local midnight in Berlin is 23:00 UTC the day before.
        assertEquals(Instant.parse("2026-11-21T23:00:00Z"), snapshot.hourly.first().instant)
        assertEquals(7_200L, snapshot.daily.first().sunshineSeconds)
        assertEquals(29_400L, snapshot.daily.first().daylightSeconds)
    }

    @Test
    fun `unix timestamps become absolute instants that do not depend on the display zone`() {
        val hourly = HourlyDto(
            time = listOf("1795302000", "1795305600"),
            temperature = listOf(1.0, 2.0),
        )

        val inBerlin = mapper.toHourly(hourly, ZoneId.of("Europe/Berlin"))
        val inTokyo = mapper.toHourly(hourly, ZoneId.of("Asia/Tokyo"))

        assertEquals(
            listOf(Instant.parse("2026-11-21T23:00:00Z"), Instant.parse("2026-11-22T00:00:00Z")),
            inBerlin.map { it.instant },
        )
        assertEquals(inBerlin.map { it.instant }, inTokyo.map { it.instant })
        // Only the rendered local time differs, and it stays one hour apart.
        assertEquals("00:00", inBerlin.first().localDateTime.toLocalTime().toString())
        assertEquals("08:00", inTokyo.first().localDateTime.toLocalTime().toString())
    }

    @Test
    fun `a current block without a usable timestamp keeps its values but reports no time`() {
        val berlin = ZoneId.of("Europe/Berlin")

        val missing = requireNotNull(
            mapper.toCurrent(CurrentDto(time = null, temperature = 6.4, weatherCode = 1), berlin),
        )
        assertNull(missing.instant)
        assertEquals(6.4, missing.temperatureCelsius!!, 0.0001)
        assertEquals(1, missing.weatherCode)

        val unparsable = requireNotNull(
            mapper.toCurrent(CurrentDto(time = "kein Datum", temperature = 6.4, weatherCode = 1), berlin),
        )
        assertNull(unparsable.instant)

        // A plausible-looking but clearly wrong epoch value is treated as missing as well.
        val implausible = requireNotNull(
            mapper.toCurrent(CurrentDto(time = "120", temperature = 6.4, weatherCode = 1), berlin),
        )
        assertNull(implausible.instant)
    }

    @Test
    fun `hourly entries with a missing or unparsable timestamp are dropped but the rest survives`() {
        val hourly = mapper.toHourly(
            HourlyDto(
                time = listOf("1795302000", "", "not-a-time", "42", "1795305600"),
                temperature = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0),
            ),
            ZoneId.of("Europe/Berlin"),
        )

        assertEquals(
            listOf(Instant.parse("2026-11-21T23:00:00Z"), Instant.parse("2026-11-22T00:00:00Z")),
            hourly.map { it.instant },
        )
        assertEquals(listOf(1.0, 5.0), hourly.map { it.temperatureCelsius })
    }

    @Test
    fun `daily dates use the offset reported by the response instead of a zone conversion`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // The response labels 2026-11-22 and says its offset is +02:00, so the stamp below stands
        // for local midnight on 22 November even though Berlin itself is at +01:00 that day.
        val stamped = mapper.toDaily(
            DailyDto(time = listOf("1795298400"), temperatureMax = listOf(7.0)),
            berlin,
            utcOffsetSeconds = 7200,
        )
        assertEquals(
            java.time.LocalDate.of(2026, 11, 22),
            stamped.single().date,
        )

        // Without a reported offset the location zone is used, which is still a real calendar day.
        val withoutOffset = mapper.toDaily(
            DailyDto(time = listOf("1795302000"), temperatureMax = listOf(7.0)),
            berlin,
            utcOffsetSeconds = null,
        )
        assertEquals(
            java.time.LocalDate.of(2026, 11, 22),
            withoutOffset.single().date,
        )
    }

    @Test
    fun `numeric time values of the unixtime format decode into the dto`() {
        val json = """
            {
              "timezone": "Europe/Berlin",
              "utc_offset_seconds": 3600,
              "current": { "time": 1795305600, "temperature_2m": 5.5 },
              "hourly": { "time": [1795302000, 1795305600], "temperature_2m": [1.0, 2.0] },
              "daily": { "time": [1795302000], "temperature_2m_max": [7.0] }
            }
        """.trimIndent()

        val decoded = requireNotNull(mapper.decodeCached(json))
        val snapshot = mapper.toSnapshot(decoded, hamburg, fetchedAt)

        assertEquals("1795305600", decoded.current?.time)
        assertEquals(listOf("1795302000", "1795305600"), decoded.hourly?.time)
        assertEquals(
            listOf(Instant.parse("2026-11-21T23:00:00Z"), Instant.parse("2026-11-22T00:00:00Z")),
            snapshot.hourly.map { it.instant },
        )
        assertEquals(Instant.parse("2026-11-22T00:00:00Z"), requireNotNull(snapshot.current).instant)
        assertEquals(java.time.LocalDate.of(2026, 11, 22), snapshot.daily.single().date)
    }

    @Test
    fun `a cached payload written with iso timestamps is still readable`() {
        val json = """
            {
              "timezone": "Europe/Berlin",
              "current": { "time": "2026-11-22T08:00", "temperature_2m": 5.5 },
              "hourly": { "time": ["2026-11-22T08:00", "2026-11-22T09:00"], "temperature_2m": [1.0, 2.0] },
              "daily": { "time": ["2026-11-22"], "temperature_2m_max": [7.0] }
            }
        """.trimIndent()

        val snapshot = mapper.toSnapshot(requireNotNull(mapper.decodeCached(json)), hamburg, fetchedAt)

        assertEquals(
            listOf(Instant.parse("2026-11-22T07:00:00Z"), Instant.parse("2026-11-22T08:00:00Z")),
            snapshot.hourly.map { it.instant },
        )
        assertEquals(Instant.parse("2026-11-22T07:00:00Z"), requireNotNull(snapshot.current).instant)
        assertEquals(java.time.LocalDate.of(2026, 11, 22), snapshot.daily.single().date)
    }

    @Test
    fun `the time zone from the api wins over the stored location time zone`() {
        val withApiZone = mapper.toSnapshot(
            dto = sampleForecastDto(timeZone = "Asia/Tokyo"),
            location = hamburg,
            fetchedAt = fetchedAt,
        )
        assertEquals(ZoneId.of("Asia/Tokyo"), withApiZone.timeZone)

        val withoutApiZone = mapper.toSnapshot(
            dto = sampleForecastDto(timeZone = null),
            location = hamburg,
            fetchedAt = fetchedAt,
        )
        assertEquals(ZoneId.of("Europe/Berlin"), withoutApiZone.timeZone)
    }

    @Test
    fun `shorter value arrays stay index aligned and missing values remain null`() {
        // ISO strings are still accepted: rows written by older app versions stay readable.
        val dto = ForecastResponseDto(
            timezone = "Europe/Berlin",
            hourly = HourlyDto(
                time = listOf("2026-11-22T08:00", "2026-11-22T09:00", "2026-11-22T10:00"),
                temperature = listOf(5.0),
                weatherCode = listOf(null, 61, null),
                precipitationProbability = listOf(20, null, 80),
            ),
        )
        val hourly = mapper.toHourly(dto.hourly, ZoneId.of("Europe/Berlin"))

        assertEquals(3, hourly.size)
        assertEquals(5.0, hourly[0].temperatureCelsius!!, 0.0001)
        assertNull(hourly[1].temperatureCelsius)
        assertNull(hourly[2].temperatureCelsius)
        assertEquals(61, hourly[1].weatherCode)
        // A missing weather code stays null instead of inventing a condition.
        assertNull(hourly[0].condition)
        assertEquals(20, hourly[0].precipitationProbabilityPercent)
        assertNull(hourly[1].precipitationProbabilityPercent)
        assertNull(hourly[2].precipitationMillimetres)
    }

    @Test
    fun `unparsable timestamps are skipped instead of breaking the whole list`() {
        val hourly = mapper.toHourly(
            HourlyDto(
                time = listOf("2026-11-22T08:00", "kein Datum", "", "2026-11-22T09:00"),
                temperature = listOf(1.0, 2.0, 3.0, 4.0),
            ),
            ZoneId.of("Europe/Berlin"),
        )
        assertEquals(2, hourly.size)
        assertEquals(1.0, hourly.first().temperatureCelsius!!, 0.0001)
        assertEquals(4.0, hourly.last().temperatureCelsius!!, 0.0001)
    }

    @Test
    fun `current block without temperature cannot be displayed and is dropped`() {
        val current = mapper.toCurrent(
            CurrentDto(time = "2026-11-22T08:00", temperature = null, weatherCode = 1),
            ZoneId.of("Europe/Berlin"),
        )
        assertNull(current)
        assertNull(mapper.toCurrent(null, ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun `daily rows distinguish zero duration from a missing value`() {
        val daily = mapper.toDaily(
            DailyDto(
                time = listOf("2026-11-22", "kaputt", "2026-11-23"),
                temperatureMax = listOf(null, 5.0, 6.0),
                sunshineDuration = listOf(0.0, 100.0, 200.0),
            ),
            ZoneId.of("Europe/Berlin"),
        )
        assertEquals(2, daily.size)
        assertNull(daily[0].maxTemperatureCelsius)
        assertEquals(0L, daily[0].sunshineSeconds)
        assertNull(daily[0].daylightSeconds)
        assertEquals(200L, daily[1].sunshineSeconds)
    }

    @Test
    fun `unknown json fields are ignored and error payloads stay readable`() {
        val withExtraFields = """
            {
              "latitude": 53.55,
              "timezone": "Europe/Berlin",
              "generated_by_ms": "open-meteo.com",
              "current": { "temperature_2m": 5.5, "unexpected_value": 42 },
              "hourly": { "time": ["2026-11-22T00:00"], "temperature_2m": [1.0] }
            }
        """.trimIndent()
        val decoded = mapper.decodeCached(withExtraFields)
        assertNotNull(decoded)
        assertEquals(5.5, requireNotNull(decoded?.current?.temperature), 0.0001)
        assertEquals(1, decoded?.hourly?.time?.size)

        val error = mapper.decodeCached("""{"error":true,"reason":"No data found for these coordinates."}""")
        assertEquals(true, error?.error)
        assertEquals("No data found for these coordinates.", error?.reason)
    }

    @Test
    fun `a payload survives the cache round trip`() {
        val original = sampleForecastDto()
        val encoded = mapper.encode(original)
        val decoded = mapper.decodeCached(encoded)

        assertEquals(original, decoded)
        val restored = mapper.toSnapshot(requireNotNull(decoded), hamburg, fetchedAt)
        assertEquals(original.current?.temperature, restored.current?.temperatureCelsius)
        assertEquals(original.hourly?.time?.size, restored.hourly.size)
    }

    @Test
    fun `corrupt cache content is reported as unusable`() {
        assertNull(mapper.decodeCached("not json at all"))
        assertNull(mapper.decodeCached(""))
    }

    @Test
    fun `geocoding results are mapped and unusable entries are dropped`() {
        val locations = mapper.toLocations(
            listOf(
                geocoderResult(id = 2911298, name = "Hamburg", latitude = 53.55, longitude = 9.99),
                geocoderResult(id = null, name = "Ohne Id", latitude = 1.5, longitude = 2.25),
                geocoderResult(id = 1, name = null, latitude = 3.0, longitude = 4.0),
                geocoderResult(id = 2, name = "  ", latitude = 3.0, longitude = 4.0),
                geocoderResult(id = 3, name = "Ohne Koordinaten", latitude = null, longitude = 4.0),
                geocoderResult(
                    id = 4,
                    name = " Tokio ",
                    latitude = 35.69,
                    longitude = 139.69,
                    country = "Japan",
                    countryCode = "JP",
                    timezone = null,
                ),
            ),
        )

        assertEquals(3, locations.size)
        val first = locations.first()
        assertEquals("2911298", first.id)
        assertEquals("Hamburg", first.name)
        assertEquals("Deutschland", first.country)

        // Results without an id fall back to a stable coordinate id.
        assertEquals("coord-1.5000-2.2500", locations[1].id)

        val tokyo = locations.last()
        assertEquals("Tokio", tokyo.name)
        assertEquals(SavedLocation.DEFAULT_TIME_ZONE_ID, tokyo.timeZoneId)
        assertEquals("JP", tokyo.countryCode)
    }

    @Test
    fun `location subtitle and duplicate detection work on the domain model`() {
        val withRegion = SavedLocation(
            id = "a",
            name = "Hamburg",
            latitude = 53.55,
            longitude = 9.99,
            admin1 = "Freie und Hansestadt Hamburg",
            country = "Deutschland",
        )
        assertEquals("Freie und Hansestadt Hamburg, Deutschland", withRegion.subtitle)

        // A different id but the same coordinates is still the same place.
        val samePlaceDifferentId = withRegion.copy(id = "b")
        assertTrue(withRegion.isSamePlaceAs(samePlaceDifferentId))
        assertFalse(withRegion.isSamePlaceAs(withRegion.copy(id = "c", latitude = 1.0)))
    }
}
