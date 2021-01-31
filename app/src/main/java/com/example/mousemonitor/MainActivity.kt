package com.example.mousemonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mousemonitor.databinding.ActivityMainBinding
import java.util.*


class MainActivity : AppCompatActivity() {

    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothService: BluetoothService? = null
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val MESSAGE_READ: Int = 0
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
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showOnScreen(readMessage: String) {
        val textBox = binding.dataTextBox
        textBox.text = readMessage
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
        binding.linearLayout.removeAllViews()
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

}