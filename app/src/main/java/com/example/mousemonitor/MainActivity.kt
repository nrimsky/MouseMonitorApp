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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.mousemonitor.Constants.MESSAGE_CONNECTED
import com.example.mousemonitor.Constants.MESSAGE_DISCONNECTED
import com.example.mousemonitor.Constants.MESSAGE_READ
import com.example.mousemonitor.databinding.ActivityMainBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet


class MainActivity : AppCompatActivity() {

    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothService: BluetoothService? = null
    private lateinit var binding: ActivityMainBinding
    private var numDatapoints: Int = 0
    private var dataBuf: String = ""

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
                    // print to console
                    Log.d(TAG, readMessage)
                    // show in app
                    showOnScreen(readMessage)
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

    private fun showOnScreen(readMessage: String) {
        if (numDatapoints > 100) {
            drawGraph(floatListFromString(dataBuf), 2.0f)
            dataBuf = ""
            numDatapoints = 0
        }
        dataBuf = "${dataBuf}$readMessage"
        numDatapoints++
    }


    private fun floatListFromString(str: String): List<Float> {
        return str
                .split(",")
                .filter { it != "" }
                .map { it.toFloat() }
    }

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
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            xAxis.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
        }
    }

    private fun drawGraph(data: List<Float>, period: Float) {
        // TO DO: Real time plotting (add one value at a time)
        // TO DO: Improve graph appearance
        val dataPoints = data.mapIndexed { i, dataPoint ->
            Entry(i*period, dataPoint)
        }
        val dataSet = LineDataSet(dataPoints, "Piezo readings")
        with(dataSet) {
            color = Color.BLUE
            valueTextColor = Color.BLUE
            highLightColor = Color.RED
            setDrawValues(false)
            lineWidth = 1.5f
            isHighlightEnabled = true
            setDrawHighlightIndicators(false)
        }
        binding.chartView.data = LineData(dataSet)
        binding.chartView.invalidate()
    }

}