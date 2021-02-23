package com.github.nrimsky.mousemonitor.dataprocessing

class MAFilterManager(private var maSize: Int = 1) {

    private var i: Int = 0
    private var maBuf = FloatArray(maSize){0f}

    fun nextValues(readings: List<Float>): List<Float>{
        val out: MutableList<Float> = mutableListOf()
        readings.forEach {
            if (i >= maSize) {
                i = 0
            }
            maBuf[i] = it
            i++
            out.add(maBuf.average().toFloat())
        }
        return out
    }

}