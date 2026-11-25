package com.example.weatherapp.domain.model

/**
 * The three intensity levels the application shows for rain and for snowfall.
 *
 * Everything finer than this stays inside [WeatherCondition]; the presentation deliberately
 * collapses the WMO gradations into light, moderate and heavy so that one, two or three
 * precipitation symbols can be recognised at a glance.
 */
enum class PrecipitationIntensity { LIGHT, MODERATE, HEAVY }

/**
 * Which kind of precipitation a symbol and a short description should express.
 *
 * Conditions that are not plain rain or plain snowfall keep their own family, so drizzle,
 * freezing precipitation, snow grains, thunderstorms and hail are never forced into a drop or
 * flake count that would describe them wrongly.
 */
enum class PrecipitationFamily {
    NONE,
    RAIN,
    SNOW,
    DRIZZLE,
    FREEZING_PRECIPITATION,
    SNOW_GRAINS,
    THUNDERSTORM,
    HAIL,
}

/** Intensity-aware precipitation presentation of one current, hourly or daily value. */
data class PrecipitationStyle(
    val family: PrecipitationFamily,
    val intensity: PrecipitationIntensity? = null,
) {
    companion object {
        /** No precipitation: the sky condition alone decides the symbol. */
        val NONE = PrecipitationStyle(PrecipitationFamily.NONE)
    }
}

/**
 * Central classification of a WMO condition plus the reported precipitation amount into the
 * three rain and three snowfall levels the whole user interface shares.
 *
 * Strategy:
 * 1. The WMO code decides the family and the base intensity. The code is the provider's own
 *    meteorological classification, so it is the primary signal.
 * 2. A reported amount (millimetres for rain, centimetres for snowfall) may refine that base
 *    level by **at most one step**, up or down. A code that says "heavy" is never demoted two
 *    steps by a small accumulation, and a code that says "light" is never promoted two steps.
 * 3. Amounts that are missing, zero, negative or not finite carry no information and never
 *    change anything. A zero simply means the provider reported no precipitation for the
 *    interval, which the condition already says.
 * 4. Precipitation *probability* is never used: a high probability says nothing about how
 *    heavy the precipitation would be.
 * 5. Daily values are not refined by their sum, because a 24 hour accumulation is not
 *    comparable with the hourly thresholds below; the daily WMO code already describes the
 *    dominant intensity of the day.
 *
 * Thresholds (per hourly interval, the unit the forecast reports them in):
 * - rain: light below 0.5 mm, moderate from 0.5 mm, heavy from 2.5 mm
 * - snowfall: light below 0.5 cm, moderate from 0.5 cm, heavy from 1.5 cm
 */
object PrecipitationClassifier {

    /** Rain of at least this many millimetres per hour is at least moderate. */
    const val MODERATE_RAIN_MILLIMETRES = 0.5

    /** Rain of at least this many millimetres per hour is heavy. */
    const val HEAVY_RAIN_MILLIMETRES = 2.5

    /** Snowfall of at least this many centimetres per hour is at least moderate. */
    const val MODERATE_SNOW_CENTIMETRES = 0.5

    /** Snowfall of at least this many centimetres per hour is heavy. */
    const val HEAVY_SNOW_CENTIMETRES = 1.5

    private val levels: List<PrecipitationIntensity> = PrecipitationIntensity.entries

    fun classify(
        condition: WeatherCondition?,
        precipitationMillimetres: Double? = null,
        snowfallCentimetres: Double? = null,
    ): PrecipitationStyle = when (condition) {
        null, WeatherCondition.UNKNOWN -> PrecipitationStyle.NONE

        WeatherCondition.LIGHT_RAIN, WeatherCondition.LIGHT_RAIN_SHOWERS ->
            rain(PrecipitationIntensity.LIGHT, precipitationMillimetres)
        WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS ->
            rain(PrecipitationIntensity.MODERATE, precipitationMillimetres)
        WeatherCondition.HEAVY_RAIN, WeatherCondition.VIOLENT_RAIN_SHOWERS ->
            rain(PrecipitationIntensity.HEAVY, precipitationMillimetres)

        WeatherCondition.LIGHT_SNOW, WeatherCondition.LIGHT_SNOW_SHOWERS ->
            snow(PrecipitationIntensity.LIGHT, snowfallCentimetres)
        WeatherCondition.SNOW ->
            snow(PrecipitationIntensity.MODERATE, snowfallCentimetres)
        WeatherCondition.HEAVY_SNOW, WeatherCondition.SNOW_SHOWERS ->
            snow(PrecipitationIntensity.HEAVY, snowfallCentimetres)

        WeatherCondition.LIGHT_DRIZZLE, WeatherCondition.DRIZZLE, WeatherCondition.DENSE_DRIZZLE ->
            PrecipitationStyle(PrecipitationFamily.DRIZZLE)
        WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.DENSE_FREEZING_DRIZZLE,
        WeatherCondition.FREEZING_RAIN, WeatherCondition.HEAVY_FREEZING_RAIN ->
            PrecipitationStyle(PrecipitationFamily.FREEZING_PRECIPITATION)
        WeatherCondition.SNOW_GRAINS -> PrecipitationStyle(PrecipitationFamily.SNOW_GRAINS)

        WeatherCondition.THUNDERSTORM -> PrecipitationStyle(PrecipitationFamily.THUNDERSTORM)
        WeatherCondition.THUNDERSTORM_WITH_HAIL, WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL ->
            PrecipitationStyle(PrecipitationFamily.HAIL)

        // Clear, mainly clear, partly cloudy, overcast and fog: no precipitation family.
        else -> PrecipitationStyle.NONE
    }

    private fun rain(
        base: PrecipitationIntensity,
        millimetres: Double?,
    ): PrecipitationStyle = PrecipitationStyle(
        family = PrecipitationFamily.RAIN,
        intensity = refine(
            base = base,
            band = amountBand(millimetres, MODERATE_RAIN_MILLIMETRES, HEAVY_RAIN_MILLIMETRES),
        ),
    )

    private fun snow(
        base: PrecipitationIntensity,
        centimetres: Double?,
    ): PrecipitationStyle = PrecipitationStyle(
        family = PrecipitationFamily.SNOW,
        intensity = refine(
            base = base,
            band = amountBand(centimetres, MODERATE_SNOW_CENTIMETRES, HEAVY_SNOW_CENTIMETRES),
        ),
    )

    /** Intensity band of a reported amount, or null when the amount carries no information. */
    private fun amountBand(
        amount: Double?,
        moderateLimit: Double,
        heavyLimit: Double,
    ): PrecipitationIntensity? {
        val value = amount?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return when {
            value >= heavyLimit -> PrecipitationIntensity.HEAVY
            value >= moderateLimit -> PrecipitationIntensity.MODERATE
            else -> PrecipitationIntensity.LIGHT
        }
    }

    /** Moves the code based level towards the reported band, but never by more than one step. */
    private fun refine(
        base: PrecipitationIntensity,
        band: PrecipitationIntensity?,
    ): PrecipitationIntensity {
        val reported = band ?: return base
        return if (reported.ordinal > base.ordinal) {
            levels[(base.ordinal + 1).coerceAtMost(reported.ordinal)]
        } else {
            levels[(base.ordinal - 1).coerceAtLeast(reported.ordinal)]
        }
    }
}
