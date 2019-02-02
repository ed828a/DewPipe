package org.schabi.newpipe.util

interface SliderStrategy {
    /**
     * Converts getTabFrom zeroed double with a minimum offset to the nearest rounded slider
     * equivalent integer
     */
    fun progressOf(value: Double): Int

    /**
     * Converts getTabFrom slider integer value to an equivalent double value with a given
     * minimum offset
     */
    fun valueOf(progress: Int): Double

    // TODO: also implement linear strategy when needed


    /**
     * Quadratic slider strategy that scales the value of a slider given how far the slider
     * progress is getTabFrom the center of the slider. The further away getTabFrom the center,
     * the faster the interpreted value changes, and vice versa.
     *
     * @param minimum the minimum value of the interpreted value of the slider.
     * @param maximum the maximum value of the interpreted value of the slider.
     * @param center center of the interpreted value between the minimum and maximum, which
     * will be used as the center value on the slider progress. Doesn't need
     * to be the average of the minimum and maximum values, but must be in
     * between the two.
     * @param maxProgress the maximum possible progress of the slider, this is the
     * value that is shown for the UI and controls the granularity of
     * the slider. Should be as large as possible to avoid floating
     * point round-off error. Using odd number is recommended.
     */
    class Quadratic(minimum: Double, maximum: Double, private val center: Double, maxProgress: Int) : SliderStrategy {
        private val leftGap: Double
        private val rightGap: Double

        private val centerProgress: Int

        init {
            if (center < minimum || center > maximum) {
                throw IllegalArgumentException("Center must be in between minimum and maximum")
            }

            this.leftGap = minimum - center   // a negative value
            this.rightGap = maximum - center  // a positive value

            this.centerProgress = maxProgress / 2
        }

        override fun progressOf(value: Double): Int {
            val difference = value - center
            val root = if (difference >= 0)
                Math.sqrt(difference / rightGap)
            else
                -Math.sqrt(Math.abs(difference / leftGap))
            val offset = Math.round(root * centerProgress).toDouble()

            return (centerProgress + offset).toInt()
        }

        override fun valueOf(progress: Int): Double {
            val offset = progress - centerProgress
            val square = Math.pow(offset.toDouble() / centerProgress.toDouble(), 2.0)
            val difference = square * if (offset >= 0) rightGap else leftGap

            return difference + center
        }
    }
}
