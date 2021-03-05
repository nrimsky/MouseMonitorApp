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
import com.github.nrimsky.mousemonitor.databinding.ActivityMainBinding
import com.github.nrimsky.mousemonitor.dataprocessing.ThresholdManager
import com.github.nrimsky.mousemonitor.helpers.setupNumberTextField
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet


class MainActivity : AppCompatActivity() {

    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothService: BluetoothService? = null
    private lateinit var binding: ActivityMainBinding

    private lateinit var data: LineData
    private var dataSet: LineDataSet = LineDataSet(mutableListOf<Entry>(), "Breathing Rate")

    // Initialise with default start values
    private var thresholdManager = ThresholdManager(DEFAULT_LOWER_THRESHOLD, DEFAULT_UPPER_THRESHOLD)

    private val maBuf = Array(5){0F}
    private var maTot = 0F
    private var maI = 0
    private var t = 0

    companion object {
        private const val PERMISSION_CODE = 1
        private const val REQUEST_ENABLE_BT = 2
        private const val TAG = "MainActivity"
        private const val DEFAULT_LOWER_THRESHOLD = 55f
        private const val DEFAULT_UPPER_THRESHOLD = 65f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MouseMonitor"
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setupGraph()
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

    private var chars: String = ""

    // Message handler for data received from bluetooth service
    private val mHandler = object : Handler(Looper.getMainLooper()) {
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    for (letter in readMessage) {
                        chars = if (letter.toString() == ",") {
                            processData(chars.toInt().toFloat())
                            ""
                        } else {
                            chars.plus(letter)
                        }
                    }
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
    private fun processData(periodMilliseconds: Float) {
        val periodSeconds = periodMilliseconds / 1000F
        val freqHz = 1F/periodSeconds
        val freqBPM = 60F*freqHz
        // Extra check for "sensible" value in case there was a bluetooth transmission lost bit
        if ((freqBPM > 30) && (freqBPM < 100)) {
            if  (maI >= maBuf.size) {
                maI = 0
            }
            maTot-=maBuf[maI]
            maBuf[maI] = freqBPM
            maTot+=maBuf[maI]
            maI++

            val newReading = maTot/maBuf.size
            addPointToGraph(newReading)
            val mouseState = thresholdManager.getState(newReading)
            binding.mainBpmDisplay.text = "$newReading BPM"
            binding.mainBpmDisplay.setBackgroundColor(getColor(mouseState))
        }
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

    // Graph

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
            axisLeft.axisMaximum = 100f
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