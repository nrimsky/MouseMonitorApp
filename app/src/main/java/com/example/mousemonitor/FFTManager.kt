package com.example.mousemonitor

import com.paramsen.noise.Noise

class FFTManager(private val fftLen: Int) {

    private val fftCalculator: Noise = Noise.real(fftLen)
    private val fftSrc: FloatArray = FloatArray(fftLen)
    private val fftDst: FloatArray = FloatArray(fftLen + 2)
    private var fftIndex: Int = 0

    private var latestFFT = FloatArray(fftLen){0f}

    private fun appendToFFT(readings: List<Float>) {
        readings.forEach {
            if (fftIndex < fftLen) {
                fftSrc[fftIndex] = it
                fftIndex++
            } else {
                latestFFT = fftCalculator.fft(fftSrc, fftDst)
                fftIndex = 0
            }
        }
    }

    fun nextValues(readings: List<Float>): FloatArray {
        appendToFFT(readings)
        return latestFFT
    }

}