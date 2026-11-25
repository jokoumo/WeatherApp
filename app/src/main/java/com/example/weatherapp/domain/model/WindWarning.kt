package com.example.weatherapp.domain.model

/**
 * How strongly the wind of one current, hourly or daily value should be highlighted.
 *
 * The level never replaces the weather symbol; it only adds a supplementary wind indicator, so
 * clouds, sunshine, rain or snow stay visible.
 */
enum class WindWarningLevel { NONE, STRONG, STORM }

/** Which measurement raised the warning, so gusts are never presented as sustained wind. */
enum class WindWarningCause { NONE, SUSTAINED_WIND, GUSTS }

/** Wind warning of a single value; [NONE] means no indicator is shown anywhere. */
data class WindWarning(
    val level: WindWarningLevel,
    val cause: WindWarningCause = WindWarningCause.NONE,
) {
    val isWarning: Boolean
        get() = level != WindWarningLevel.NONE

    /** True when only the transient gusts, not the sustained wind, crossed a threshold. */
    val causedByGusts: Boolean
        get() = cause == WindWarningCause.GUSTS

    companion object {
        val NONE = WindWarning(WindWarningLevel.NONE, WindWarningCause.NONE)
    }
}

/**
 * Central wind classification shared by every screen, so the same conditions can never produce
 * different indicators in different places.
 *
 * Sustained wind and gusts are evaluated separately and against separate thresholds:
 *
 * | Measurement | Strong wind | Storm |
 * | --- | --- | --- |
 * | Sustained wind | 39 km/h | 75 km/h |
 * | Gusts | 50 km/h | 85 km/h |
 *
 * The sustained thresholds are the lower bounds of Beaufort 6 ("starker Wind") and Beaufort 9
 * ("Sturm"). Gusts are short peaks, so they are only reported as a warning from higher values;
 * the cause is kept in [WindWarningCause] and named in the label, which is why a gust warning
 * reads "Starke Windböen" instead of claiming a sustained wind that never blew.
 *
 * The higher of the two levels wins. Missing, negative or non-finite speeds are treated as
 * unknown and never produce a warning.
 */
object WindWarningClassifier {

    /** Sustained wind from which the neutral strong-wind indicator is shown. */
    const val STRONG_SUSTAINED_KMH = 39.0

    /** Sustained wind from which the red storm indicator is shown. */
    const val STORM_SUSTAINED_KMH = 75.0

    /** Gust speed from which the neutral strong-wind indicator is shown. */
    const val STRONG_GUST_KMH = 50.0

    /** Gust speed from which the red storm indicator is shown. */
    const val STORM_GUST_KMH = 85.0

    fun classify(
        sustainedKilometresPerHour: Double?,
        gustsKilometresPerHour: Double?,
    ): WindWarning {
        val sustainedLevel = levelOf(
            speed = sustainedKilometresPerHour,
            strongThreshold = STRONG_SUSTAINED_KMH,
            stormThreshold = STORM_SUSTAINED_KMH,
        )
        val gustLevel = levelOf(
            speed = gustsKilometresPerHour,
            strongThreshold = STRONG_GUST_KMH,
            stormThreshold = STORM_GUST_KMH,
        )
        return when {
            gustLevel.ordinal > sustainedLevel.ordinal ->
                WindWarning(gustLevel, WindWarningCause.GUSTS)
            sustainedLevel == WindWarningLevel.NONE -> WindWarning.NONE
            else -> WindWarning(sustainedLevel, WindWarningCause.SUSTAINED_WIND)
        }
    }

    private fun levelOf(
        speed: Double?,
        strongThreshold: Double,
        stormThreshold: Double,
    ): WindWarningLevel {
        val value = speed?.takeIf { it.isFinite() && it >= 0.0 } ?: return WindWarningLevel.NONE
        return when {
            value >= stormThreshold -> WindWarningLevel.STORM
            value >= strongThreshold -> WindWarningLevel.STRONG
            else -> WindWarningLevel.NONE
        }
    }
}
