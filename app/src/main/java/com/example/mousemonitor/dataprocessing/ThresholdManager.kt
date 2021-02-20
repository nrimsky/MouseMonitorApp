package com.example.mousemonitor.dataprocessing


class ThresholdManager(val upperThreshold: Float, val lowerThreshold: Float) {

    private val centre = (lowerThreshold+upperThreshold)/2
    private val deviation = upperThreshold-centre
    private val warningBelow = centre-(deviation*0.8)
    private val warningAbove = centre+(deviation*0.8)

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