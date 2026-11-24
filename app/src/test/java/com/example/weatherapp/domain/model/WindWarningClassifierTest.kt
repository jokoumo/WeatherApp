package com.example.weatherapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wind warning thresholds.
 *
 * Sustained wind and gusts are judged separately, so the tests check the exact boundary values
 * of all four thresholds, which measurement a warning is attributed to, and that unusable wind
 * data never produces a warning.
 */
class WindWarningClassifierTest {

    private fun classify(sustained: Double?, gusts: Double? = null): WindWarning =
        WindWarningClassifier.classify(sustained, gusts)

    @Test
    fun `ordinary wind produces no indicator`() {
        assertEquals(WindWarning.NONE, classify(0.0))
        assertEquals(WindWarning.NONE, classify(12.0))
        assertEquals(WindWarning.NONE, classify(38.9))
    }

    @Test
    fun `the sustained strong wind threshold is inclusive`() {
        assertEquals(
            WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND),
            classify(WindWarningClassifier.STRONG_SUSTAINED_KMH),
        )
        assertEquals(
            WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND),
            classify(60.0),
        )
    }

    @Test
    fun `the sustained storm threshold is inclusive`() {
        assertEquals(
            WindWarning(WindWarningLevel.STORM, WindWarningCause.SUSTAINED_WIND),
            classify(WindWarningClassifier.STORM_SUSTAINED_KMH),
        )
        assertEquals(
            WindWarning(WindWarningLevel.STORM, WindWarningCause.SUSTAINED_WIND),
            classify(140.0),
        )
        // Just below the storm threshold it stays the neutral strong wind warning.
        assertEquals(
            WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND),
            classify(WindWarningClassifier.STORM_SUSTAINED_KMH - 0.1),
        )
    }

    @Test
    fun `strong gusts warn even though the sustained wind stays below its threshold`() {
        assertEquals(
            WindWarning(WindWarningLevel.STRONG, WindWarningCause.GUSTS),
            classify(20.0, WindWarningClassifier.STRONG_GUST_KMH),
        )
        assertEquals(WindWarning.NONE, classify(20.0, WindWarningClassifier.STRONG_GUST_KMH - 0.1))
    }

    @Test
    fun `storm gusts raise a storm warning attributed to the gusts`() {
        assertEquals(
            WindWarning(WindWarningLevel.STORM, WindWarningCause.GUSTS),
            classify(40.0, WindWarningClassifier.STORM_GUST_KMH),
        )
        assertEquals(
            WindWarning(WindWarningLevel.STORM, WindWarningCause.GUSTS),
            classify(30.0, 120.0),
        )
    }

    @Test
    fun `the stronger of the two measurements decides the level`() {
        // Sustained wind is already a storm, the gusts cannot make it stronger.
        assertEquals(
            WindWarning(WindWarningLevel.STORM, WindWarningCause.SUSTAINED_WIND),
            classify(80.0, 90.0),
        )
        // Equal levels are attributed to the sustained wind, which is the stronger statement.
        assertEquals(
            WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND),
            classify(45.0, 60.0),
        )
    }

    @Test
    fun `gust thresholds are higher than the sustained ones`() {
        assertTrue(
            WindWarningClassifier.STRONG_GUST_KMH > WindWarningClassifier.STRONG_SUSTAINED_KMH,
        )
        assertTrue(
            WindWarningClassifier.STORM_GUST_KMH > WindWarningClassifier.STORM_SUSTAINED_KMH,
        )
    }

    @Test
    fun `missing or unusable wind data never produces a warning`() {
        assertEquals(WindWarning.NONE, classify(null, null))
        assertEquals(WindWarning.NONE, classify(-5.0, -80.0))
        assertEquals(WindWarning.NONE, classify(Double.NaN, Double.NaN))
        assertEquals(WindWarning.NONE, classify(Double.POSITIVE_INFINITY, Double.NaN))
    }

    @Test
    fun `a gust measurement still warns when the sustained wind is unknown`() {
        assertEquals(
            WindWarning(WindWarningLevel.STORM, WindWarningCause.GUSTS),
            classify(null, 120.0),
        )
    }

    @Test
    fun `a warning without a level is never reported as a warning`() {
        assertFalse(WindWarning.NONE.isWarning)
        assertFalse(WindWarning.NONE.causedByGusts)
        assertTrue(classify(45.0).isWarning)
        assertTrue(classify(20.0, 70.0).causedByGusts)
    }
}
