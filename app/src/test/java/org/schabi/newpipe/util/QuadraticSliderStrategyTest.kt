package org.schabi.newpipe.util

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class QuadraticSliderStrategyTest {

    private val standard = SliderStrategy.Quadratic(0.0, 100.0, 50.0, STEP)
    @Test
    fun testLeftBound() {
        assertEquals(standard.progressOf(0.0).toLong(), 0)
        assertEquals(standard.valueOf(0), 0.0, DELTA.toDouble())
    }

    @Test
    fun testCenter() {
        assertEquals(standard.progressOf(50.0).toLong(), 50)
        assertEquals(standard.valueOf(50), 50.0, DELTA.toDouble())
    }

    @Test
    fun testRightBound() {
        assertEquals(standard.progressOf(100.0).toLong(), 100)
        assertEquals(standard.valueOf(100), 100.0, DELTA.toDouble())
    }

    @Test
    fun testLeftRegion() {
        val leftProgress = standard.progressOf(25.0)
        val leftValue = standard.valueOf(25)
        assertTrue(leftProgress in 1..49)
        assertTrue(leftValue > 0f && leftValue < 50)
    }

    @Test
    fun testRightRegion() {
        val leftProgress = standard.progressOf(75.0)
        val leftValue = standard.valueOf(75)
        assertTrue(leftProgress in 51..99)
        assertTrue(leftValue > 50f && leftValue < 100)
    }

    @Test
    fun testConversion() {
        assertEquals(standard.progressOf(standard.valueOf(0)).toLong(), 0)
        assertEquals(standard.progressOf(standard.valueOf(25)).toLong(), 25)
        assertEquals(standard.progressOf(standard.valueOf(50)).toLong(), 50)
        assertEquals(standard.progressOf(standard.valueOf(75)).toLong(), 75)
        assertEquals(standard.progressOf(standard.valueOf(100)).toLong(), 100)
    }

    @Test
    fun testReverseConversion() {
        // Need a larger delta since step size / granularity is too small and causes
        // floating point round-off errors during conversion
        val largeDelta = 1f

        assertEquals(standard.valueOf(standard.progressOf(0.0)), 0.0, largeDelta.toDouble())
        assertEquals(standard.valueOf(standard.progressOf(25.0)), 25.0, largeDelta.toDouble())
        assertEquals(standard.valueOf(standard.progressOf(50.0)), 50.0, largeDelta.toDouble())
        assertEquals(standard.valueOf(standard.progressOf(75.0)), 75.0, largeDelta.toDouble())
        assertEquals(standard.valueOf(standard.progressOf(100.0)), 100.0, largeDelta.toDouble())
    }

    @Test
    fun testQuadraticPropertyLeftRegion() {
        val differenceCloserToCenter = Math.abs(standard.valueOf(40) - standard.valueOf(45))
        val differenceFurtherFromCenter = Math.abs(standard.valueOf(10) - standard.valueOf(15))
        assertTrue(differenceCloserToCenter < differenceFurtherFromCenter)
    }

    @Test
    fun testQuadraticPropertyRightRegion() {
        val differenceCloserToCenter = Math.abs(standard.valueOf(75) - standard.valueOf(70))
        val differenceFurtherFromCenter = Math.abs(standard.valueOf(95) - standard.valueOf(90))
        assertTrue(differenceCloserToCenter < differenceFurtherFromCenter)
    }

    companion object {
        private const val STEP = 100
        private const val DELTA = 1f / STEP.toFloat()
    }
}
