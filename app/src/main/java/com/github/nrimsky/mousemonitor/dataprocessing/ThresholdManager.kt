package com.github.nrimsky.mousemonitor.dataprocessing


class ThresholdManager(val lowerThreshold: Float, val upperThreshold: Float) {

    private val deviation = (upperThreshold - lowerThreshold)/2
    private val warningBelow = lowerThreshold + deviation*0.2
    private val warningAbove = upperThreshold - deviation*0.2

    enum class STATE {
        OK, WARNING, DANGER
    }

    fun getState(breathingRate: Float): STATE {
        return if ((breathingRate < lowerThreshold) || (breathingRate > upperThreshold)) {
            STATE.DANGER
        } else if ((breathingRate < warningBelow) || (breathingRate > warningAbove)) {
            STATE.WARNING
        } else {
            STATE.OK
        }
    }

}