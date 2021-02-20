package com.example.mousemonitor.dataprocessing

import android.util.Log
import com.github.mikephil.charting.data.Entry
import com.paramsen.noise.Noise
import kotlin.math.absoluteValue

class FFTManager(private val fftLen: Int) {

    private val fftCalculator: Noise = Noise.real(fftLen)
    private val fftSrc: FloatArray = FloatArray(fftLen)
    private val fftDst: FloatArray = FloatArray(fftLen + 2)
    private var fftIndex: Int = 0

    private var latestFFT = FloatArray(fftLen){0f}

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
                latestFFT = fftCalculator.fft(fftSrc, fftDst)
                fftIndex = 0
            }
        }
    }

    fun nextValues(readings: List<Float>): FloatArray {
        appendToFFT(readings)
        return latestFFT
    }

    fun maxFreq(samplingFreq: Int): Float {

        // TODO: FIX THIS - make it work and make it more efficient (don't recalculate real values of FFT)

        val fft = FloatArray(latestFFT.size/2)

        for (i in 0 until latestFFT.size / 2) {
            // Add real values of fft to graph data
            val real: Float = latestFFT[i * 2].absoluteValue
            fft[i] = real
        }

        val freqResolution: Float = (samplingFreq.toFloat()/fftLen.toFloat())/60

        var indexOfMax = 0
        var max = 0f

        fft.forEachIndexed { i, value ->
            if (value > max) {
                max = value
                indexOfMax = i
            }
        }

        return indexOfMax*freqResolution
    }

}