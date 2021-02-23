package com.github.nrimsky.mousemonitor.dataprocessing

import com.paramsen.noise.Noise
import kotlin.math.absoluteValue

class FFTManager(private val fftLen: Int) {

    companion object {
        // Breathing rates outside this range are probably errors so ignore them
        const val MINIMUM_REASONABLE_BPM = 20f
        const val MAXIMUM_REASONABLE_BPM = 100f
    }

    private val fftCalculator: Noise = Noise.real(fftLen)
    private val fftSrc: FloatArray = FloatArray(fftLen){0f}
    private val fftDst: FloatArray = FloatArray(fftLen + 2){0f}
    // Real values of fft
    private var latestFFT = FloatArray(fftLen/2){0f}
    // Index into fftSrc array where to place next reading
    private var fftI = 0

    // Used for calculating moving average of breathing rates
    private var breathingRates = FloatArray(10){0f}
    // Index into breathingRates array where to place next value
    private var brI = 0


    private fun appendToFFT(readings: List<Float>) {
        // Add new readings to fftScr array
        readings.forEach {
            if (fftI > fftLen-1) {
                fftI = 0
            }
            fftSrc[fftI] = it
            fftI++
        }
        // Calculate a new fft which includes the new readings
        val fft = fftCalculator.fft(fftSrc, fftDst)
        for (i in 0 until (fft.size / 2)-1) {
            // Add real values of fft to latestFFT
            val real: Float = fft[i * 2].absoluteValue
            latestFFT[i] = real
        }
    }

    fun nextValues(readings: List<Float>): FloatArray {
        // Returns the fft of a new window with the new readings
        appendToFFT(readings)
        return latestFFT
    }

    fun maxFreq(samplingFreq: Int): Float {

        val frequencyResolutionBPM = (samplingFreq/fftLen.toFloat())*60f

        // Ignore high values between minIndex and maxIndex as these are errors
        val minIndex = (MINIMUM_REASONABLE_BPM/frequencyResolutionBPM).toInt()
        var maxIndex = (MAXIMUM_REASONABLE_BPM/frequencyResolutionBPM).toInt()
        if (maxIndex > latestFFT.size/2) { maxIndex = (latestFFT.size/2) - 1}

        var indexOfMax = 0
        var max = 0f

        // Find peak in fft
        for (i in minIndex..maxIndex) {
            if (latestFFT[i] > max) {
                max = latestFFT[i]
                indexOfMax = i
            }
        }

        // Return average fft peak over last 10 fft windows
        if (brI >= 10) {
            brI = 0
        }
        breathingRates[brI] = indexOfMax*frequencyResolutionBPM

        return breathingRates.average().toFloat()
    }

}