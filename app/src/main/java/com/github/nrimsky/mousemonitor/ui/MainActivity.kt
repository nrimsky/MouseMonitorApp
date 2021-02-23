package com.github.nrimsky.mousemonitor.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.XAxis
import com.github.nrimsky.mousemonitor.R
import com.github.nrimsky.mousemonitor.service.BluetoothService
import com.github.nrimsky.mousemonitor.helpers.Constants.MESSAGE_CONNECTED
import com.github.nrimsky.mousemonitor.helpers.Constants.MESSAGE_DISCONNECTED
import com.github.nrimsky.mousemonitor.helpers.Constants.MESSAGE_READ
import com.github.nrimsky.mousemonitor.dataprocessing.FFTManager
import com.github.nrimsky.mousemonitor.dataprocessing.MAFilterManager
import com.github.nrimsky.mousemonitor.databinding.ActivityMainBinding
import com.github.nrimsky.mousemonitor.dataprocessing.ThresholdManager
import com.github.nrimsky.mousemonitor.floatListFromString
import com.github.nrimsky.mousemonitor.helpers.setupNumberTextField
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlin.math.pow


class MainActivity : AppCompatActivity() {

    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothService: BluetoothService? = null
    private lateinit var binding: ActivityMainBinding

    private lateinit var data: LineData
    private var dataSet: LineDataSet = LineDataSet(mutableListOf<Entry>(), "Piezo readings")

    private lateinit var fftData: LineData
    private var fftDataSet: LineDataSet = LineDataSet(
        mutableListOf<Entry>(),
        "FFT (Beats per Minute)"
    )
    private var t = 0

    // Initialise with default start values
    private var fftManager = FFTManager(DEFAULT_FFT_LEN)
    private var fftSize: Int = DEFAULT_FFT_LEN
    private var maManager = MAFilterManager(DEFAULT_MA_SIZE)
    private var thresholdManager = ThresholdManager(DEFAULT_LOWER_THRESHOLD, DEFAULT_UPPER_THRESHOLD)
    private var samplingFrequency = DEFAULT_SAMPLING_FREQUENCY

    companion object {
        private const val PERMISSION_CODE = 1
        private const val REQUEST_ENABLE_BT = 2
        private const val TAG = "MainActivity"
        // In Hz
        private const val DEFAULT_SAMPLING_FREQUENCY = 10
        private const val DEFAULT_MA_SIZE = 1
        private const val MA_MAX_SIZE = 10
        private const val MA_MIN_SIZE = 1
        private const val DEFAULT_FFT_LEN = 1024
        private const val DEFAULT_LOWER_THRESHOLD = 55f
        private const val DEFAULT_UPPER_THRESHOLD = 65f
        // 128, 256, 512, 1024, 2056
        private val FFT_LEN_OPTIONS = arrayOf(7, 8, 9, 10, 11).map { 2f.pow(it).toString() }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MouseMonitor"
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setupGraph()
        setupFFTGraph()
        setupSamplingFrequencyInput()
        setupMovingAverage()
        setupFFTSizePicker()
        setupThresholdChoosers()

        checkBluetoothPermissions()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            val alert = AppAlert(
                this,
                "Your device does not support bluetooth",
                "This app cannot be used without bluetooth"
            )
            alert.show("Ok"){ _, _ ->
                // Quits app
                this.finishAffinity()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        when {
            bluetoothAdapter == null -> {
                val alert = AppAlert(
                    this,
                    "Your device does not support bluetooth",
                    "This app cannot be used without bluetooth"
                )
                alert.show("Ok"){ _, _ ->
                    // Quits app
                    this.finishAffinity()
                }
            }
            bluetoothAdapter?.isEnabled == false -> {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            bluetoothService == null -> {
                setupBluetoothService()
            }
        }
        queryPairedDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService?.cancel()
    }

    // Message handler for data received from bluetooth service
    private val mHandler = object : Handler(Looper.getMainLooper()) {
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    processData(readMessage)
                }
                MESSAGE_CONNECTED -> {
                    val deviceName = msg.obj as String
                    binding.availableDeviceTitle.text = "$deviceName Connected"
                    binding.linearLayout.removeAllViews()
                }
                MESSAGE_DISCONNECTED -> {
                    binding.availableDeviceTitle.text = "Disconnected"
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processData(readMessage: String) {
        val readings = floatListFromString(readMessage)
        showFFTOnScreen(fftManager.nextValues(readings))
        addPointsToGraph(maManager.nextValues(readings))
        val breathingRate = fftManager.maxFreq(samplingFrequency)
        val mouseState = thresholdManager.getState(breathingRate)
        binding.mainBpmDisplay.text = "$breathingRate BPM"
        binding.mainBpmDisplay.setBackgroundColor(getColor(mouseState))
    }

    // Bluetooth

    private fun setupBluetoothService() {
        bluetoothService = BluetoothService(mHandler)
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    baseContext,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_CODE
                )
            }
        }
    }

    private fun queryPairedDevices() {
        val pairedDevices: Set<BluetoothDevice>? =bluetoothAdapter?.bondedDevices
        bluetoothAdapter?.cancelDiscovery()
        if (pairedDevices != null && pairedDevices.isNotEmpty()) {
            binding.linearLayout.removeAllViews()
        }
        pairedDevices?.forEach { device ->
            addButtonForDevice(device)
        }
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        bluetoothService?.connect(device)
    }

    private fun addButtonForDevice(device: BluetoothDevice) {
        val button = Button(this)
        button.text = device.name
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setOnClickListener {
            connectToBluetoothDevice(device)
        }
        binding.linearLayout.addView(button)
    }

    // FFT

    private fun showFFTOnScreen(fft: FloatArray) {
        val frequencyResolution = samplingFrequency.toFloat()/fftSize.toFloat()
        fftDataSet.clear()
        fft.forEachIndexed { i, fftVal ->
            fftDataSet.addEntry(Entry(i * frequencyResolution * 60, fftVal))
        }
        fftData.notifyDataChanged()
        binding.fftChart.notifyDataSetChanged()
        binding.fftChart.invalidate()
    }

    private fun setupFFTGraph() {
        with(binding.fftChart) {
            setBackgroundColor(Color.WHITE)
            axisLeft.isEnabled = true
            axisRight.isEnabled = false
            xAxis.isEnabled = true
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawLabels(true)
            legend.isEnabled = false
            description.isEnabled = false
            axisLeft.setDrawGridLines(false)
            xAxis.setDrawGridLines(true)
            axisLeft.setDrawZeroLine(true)
            xAxis.axisMaximum = 150f
            xAxis.axisMinimum = 10f
        }
        with(fftDataSet) {
            color = Color.BLUE
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }
        fftData = LineData(fftDataSet)
        binding.fftChart.data = fftData
    }

    // Main Graph

    private fun setupGraph() {
        with(binding.chartView) {
            setBackgroundColor(Color.WHITE)
            axisLeft.isEnabled = true
            axisRight.isEnabled = false
            xAxis.isEnabled = true
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawLabels(false)
            legend.isEnabled = false
            description.isEnabled = false
            axisLeft.setDrawGridLines(false)
            xAxis.setDrawGridLines(false)
            axisLeft.axisMaximum = 250f
            axisLeft.axisMinimum = 0f
            axisLeft.setDrawZeroLine(true)
        }
        with(dataSet) {
            color = Color.BLUE
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)

        }
        data = LineData(dataSet)
        binding.chartView.data = data
    }

    private fun addPointsToGraph(points: List<Float>) {
        val dataPoints = points.map { dataPoint ->
            t += 1
            Entry(t.toFloat(), dataPoint)
        }
        dataPoints.forEach {
            dataSet.addEntry(it)
            if (dataSet.values.size > 100) {
                dataSet.removeFirst()
            }
        }
        data.notifyDataChanged()
        binding.chartView.notifyDataSetChanged()
        binding.chartView.invalidate()
    }

    private fun addPointToGraph(point: Float) {
        t += 1
        val dataPoint = Entry(t.toFloat(), point)
        dataSet.addEntry(dataPoint)
        if (dataSet.values.size > 100) {
            dataSet.removeFirst()
        }
        data.notifyDataChanged()
        binding.chartView.notifyDataSetChanged()
        binding.chartView.invalidate()
    }

    // Moving average picker

    private fun setupMovingAverage() {
        with(binding.movingAverageSizePicker) {
            maxValue = MA_MAX_SIZE
            minValue = MA_MIN_SIZE
            value = DEFAULT_MA_SIZE
        }
        binding.movingAverageSizePicker.setOnValueChangedListener { _, _, newVal ->
            Log.d(TAG, "Moving average filter size changed to $newVal")
            maManager = MAFilterManager(newVal)
        }
    }

    // FFT Size picker

    private fun setupFFTSizePicker() {
        with(binding.fftSizePicker) {
            maxValue = FFT_LEN_OPTIONS.size - 1
            minValue = 0
            displayedValues = FFT_LEN_OPTIONS
            // Corresponds to 1024 samples
            value = 3
        }
        binding.fftSizePicker.setOnValueChangedListener { _, _, newVal ->
            fftSize = 2f.pow(newVal + 7).toInt()
            Log.d(TAG, "FFT size changed to $fftSize")
            fftManager = FFTManager(fftSize)
        }
    }

    // Sampling frequency input

    private fun setupSamplingFrequencyInput() {
        setupNumberTextField(binding.samplingFreqInput, DEFAULT_SAMPLING_FREQUENCY.toString())
        binding.samplingFreqInput.setOnEditorActionListener { _, _, _ ->
            var handled = false
            binding.samplingFreqInput.text.toString().toIntOrNull()?.let {
                samplingFrequency = it
                Log.d(TAG, "Sampling frequency changed to $samplingFrequency")
                hideKeyboard()
                handled = true
            }?:run{
                binding.samplingFreqInput.setText(DEFAULT_SAMPLING_FREQUENCY.toString())
                hideKeyboard()
            }
            return@setOnEditorActionListener handled
        }
    }

    fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }



    // Threshold choosers

    private fun setupThresholdChoosers() {
        val lowerInput = binding.lowerThreshold
        val upperInput = binding.upperThreshold
        val lowerDefault = DEFAULT_LOWER_THRESHOLD.toInt().toString()
        val upperDefault = DEFAULT_UPPER_THRESHOLD.toInt().toString()

        setupNumberTextField(lowerInput, lowerDefault)
        setupNumberTextField(upperInput, upperDefault)

        lowerInput.setOnEditorActionListener { _, _, _ ->
            var handled = false
            lowerInput.text.toString().toFloatOrNull()?.let { newLower ->
                thresholdManager = ThresholdManager(newLower, thresholdManager.upperThreshold)
                hideKeyboard()
                handled = true
            } ?: run {
                lowerInput.setText(lowerDefault)
                hideKeyboard()
            }
            return@setOnEditorActionListener handled
        }

        upperInput.setOnEditorActionListener { _, _, _ ->
            var handled = false
            upperInput.text.toString().toFloatOrNull()?.let { newUpper ->
                thresholdManager = ThresholdManager(thresholdManager.lowerThreshold, newUpper)
                hideKeyboard()
                handled = true
            } ?: run {
                upperInput.setText(upperDefault)
                hideKeyboard()
            }
            return@setOnEditorActionListener handled
        }
    }

    private fun getColor(mouseState: ThresholdManager.STATE): Int {
        return when (mouseState) {
            ThresholdManager.STATE.DANGER -> {
                ContextCompat.getColor(this, R.color.danger_red)
            }
            ThresholdManager.STATE.WARNING -> {
                ContextCompat.getColor(this, R.color.warning_orange)
            }
            ThresholdManager.STATE.OK -> {
                ContextCompat.getColor(this, R.color.ok_blue)
            }
        }
    }




}