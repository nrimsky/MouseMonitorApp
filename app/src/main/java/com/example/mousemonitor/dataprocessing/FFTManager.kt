package com.example.mousemonitor.dataprocessing

import com.paramsen.noise.Noise
import kotlin.math.absoluteValue

class FFTManager(private val fftLen: Int) {

    companion object {
        // Breathing rates outside this range are probably errors so ignore them
        const val MINIMUM_REASONABLE_BPM = 20f
        const val MAXIMUM_REASONABLE_BPM = 100f
    }


    private val fftCalculator: Noise = Noise.real(fftLen)
    private val fftSrc: FloatArray = FloatArray(fftLen)
    private val fftDst: FloatArray = FloatArray(fftLen + 2)
    private var fftIndex: Int = 0

    private var latestFFT = FloatArray(fftLen/2){0f}

    private fun appendToFFT(readings: List<Float>) {
        readings.forEach {
            if (fftIndex < fftLen) {
                // Keep adding to FFT buffer until full
                fftSrc[fftIndex] = it
                fftIndex++
            } else {
                // Remove DC offset
                val avg = fftSrc.average().toFloat()
                fftSrc.forEachIndexed { i, value -> fftSrc[i] = value - avg }
                // Calculate FFT
                val fft = fftCalculator.fft(fftSrc, fftDst)
                for (i in 0 until (fft.size / 2)-1) {
                    // Add real values of fft to latestFFT
                    val real: Float = fft[i * 2].absoluteValue
                    latestFFT[i] = real
                }
                fftIndex = 0
            }
        }
    }

    fun nextValues(readings: List<Float>): FloatArray {
        appendToFFT(readings)
        return latestFFT
    }

    fun maxFreq(samplingFreq: Int): Float {

        val frequencyResolutionBPM = (samplingFreq/fftLen.toFloat())*60f
        val minIndex = (MINIMUM_REASONABLE_BPM/frequencyResolutionBPM).toInt()
        var maxIndex = (MAXIMUM_REASONABLE_BPM/frequencyResolutionBPM).toInt()
        if (maxIndex > latestFFT.size/2) { maxIndex = (latestFFT.size/2) - 1}

        var indexOfMax = 0
        var max = 0f

        for (i in minIndex..maxIndex) {
            if (latestFFT[i] > max) {
                max = latestFFT[i]
                indexOfMax = i
            }
        }

        return indexOfMax*frequencyResolutionBPM
    }

}