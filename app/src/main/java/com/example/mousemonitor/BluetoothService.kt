package com.example.mousemonitor

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.*


class BluetoothService(
        private val handler: Handler
) {
    companion object {
        private const val TAG = "BluetoothService"
        const val MESSAGE_READ: Int = 0
    }

    private var mConnectedThread: ConnectedThread? = null
    private var mConnectThread: ConnectThread? = null

    @Synchronized
    fun connect(device: BluetoothDevice) {
        mConnectThread = ConnectThread(device)
        mConnectThread?.start()
    }

    @Synchronized
    fun cancel() {
        mConnectedThread?.cancel()
        mConnectThread?.cancel()
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(
                    "00001101-0000-1000-8000-00805F9B34FB"
            ))}


        override fun run() {

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                mConnectedThread = ConnectedThread(socket)
                mConnectedThread?.start()
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }


    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: Exception) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
                // Send the obtained bytes to the UI activity.
                val readMsg = handler.obtainMessage(
                        MESSAGE_READ, numBytes, -1,
                        mmBuffer
                )
                readMsg.sendToTarget()

            }
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }
}