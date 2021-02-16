package com.example.mousemonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mousemonitor.Constants.MESSAGE_CONNECTED
import com.example.mousemonitor.Constants.MESSAGE_DISCONNECTED
import com.example.mousemonitor.Constants.MESSAGE_READ
import com.example.mousemonitor.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.paramsen.noise.Noise
import kotlin.math.absoluteValue


class MainActivity : AppCompatActivity(), NumberPicker.OnValueChangeListener {

    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothService: BluetoothService? = null
    private lateinit var binding: ActivityMainBinding

    private lateinit var data: LineData
    private var dataSet: LineDataSet = LineDataSet(mutableListOf<Entry>(), "Piezo readings")

    private lateinit var fftData: LineData
    private var fftDataSet: LineDataSet = LineDataSet(mutableListOf<Entry>(), "FFT (Beats per Minute)")

    private var t: Float = 0.0f
    private var maSize: Int = 1
    private var maBuf: MutableList<Float> = mutableListOf()

    private val fftLen: Int = 2048
    private val fftCalculator = Noise.real(fftLen)
    private val fftSrc = FloatArray(fftLen)
    private val fftDst = FloatArray(fftLen + 2)
    private var fftIndex: Int = 0
    private val arduinoSamplingFrequency: Float = 100f

    companion object {
        private const val PERMISSION_CODE = 1
        private const val REQUEST_ENABLE_BT = 2
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MouseMonitor"
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setupGraph()
        setupFFTGraph()
        setupMovingAverage()

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

    private fun processData(readMessage: String) {
        val values = floatListFromString(readMessage)

        // Add values to FFT buffer
        values.forEach { appendToFFT(it) }

        if (maSize == 1) {
            addPointsToGraph(values)
        } else {
            values.forEach {
                maBuf.add(it)
                // Need to check this condition as moving average size can be increased on the fly
                if (maBuf.size > maSize) {
                    val last = maBuf.takeLast(maSize)
                    addPointToGraph(last.average().toFloat())
                    maBuf = last.toMutableList()
                }
            }
        }


    }

    private fun floatListFromString(str: String): List<Float> {
        return str
                .split(",")
                .filter { it != "" }
                .map { it.toFloat() }
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

    private fun appendToFFT(value: Float) {
        if (fftIndex < fftLen) {
            fftSrc[fftIndex] = value
            fftIndex++
        } else {
            val fft = fftCalculator.fft(fftSrc, fftDst)
            showFFTOnScreen(fft)
            fftIndex = 0
        }
    }

    private fun showFFTOnScreen(fft: FloatArray) {

        val frequencyResolution: Float = arduinoSamplingFrequency/fftLen

        Log.d(TAG,frequencyResolution.toString())

        fftDataSet.clear()

        for (i in 0 until fft.size / 2) {
            // Add real values of fft to graph data
            val real: Float = fft[i * 2].absoluteValue
            fftDataSet.addEntry(Entry(i.toFloat()*frequencyResolution*60, real))
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
            xAxis.axisMinimum = 0f
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
            t += 2.0f
            Entry(t, dataPoint)
        }
        dataPoints.forEach {
            dataSet.addEntry(it)
        }
        while (dataSet.values.size > 100) {
            dataSet.removeFirst()
        }
        data.notifyDataChanged()
        binding.chartView.notifyDataSetChanged()
        binding.chartView.invalidate()
    }

    private fun addPointToGraph(point: Float) {
        t += 2.0f
        val dataPoint = Entry(t, point)
        dataSet.addEntry(dataPoint)
        while (dataSet.values.size > 100) {
            dataSet.removeFirst()
        }
        data.notifyDataChanged()
        binding.chartView.notifyDataSetChanged()
        binding.chartView.invalidate()
    }

    // Moving average

    private fun setupMovingAverage() {
        with(binding.movingAverageSizePicker) {
            maxValue = 50
            minValue = 1
            value = 1
        }
        binding.movingAverageSizePicker.setOnValueChangedListener(this)
    }

    override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
        maSize = newVal
        Log.d(TAG, maSize.toString())
    }


}