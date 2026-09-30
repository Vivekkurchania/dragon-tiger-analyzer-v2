package com.vivek.dt.sender

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {

        private const val REQUEST_PERMISSIONS = 1001

        private val SERVICE_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothSocket: BluetoothSocket? = null

    private var outputStream: OutputStream? = null

    private var selectedDevice: BluetoothDevice? = null

    private lateinit var statusText: TextView

    private lateinit var connectButton: Button

    private lateinit var dragonButton: Button

    private lateinit var tigerButton: Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        bluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter()

        createUI()

        requestPermissions()
    }

    private fun createUI() {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            30,
            30,
            30,
            30
        )

        val title =
            TextView(this)

        title.text =
            "📱 DRAGON TIGER SENDER"

        title.textSize =
            24f

        layout.addView(title)

        statusText =
            TextView(this)

        statusText.text =
            "Status: Ready"

        statusText.textSize =
            18f

        statusText.setPadding(
            0,
            30,
            0,
            30
        )

        layout.addView(statusText)

        val scanButton =
            Button(this)

        scanButton.text =
            "📷 SCAN ANALYZER QR"

        scanButton.setOnClickListener {

            scanQR()
        }

        layout.addView(scanButton)

        connectButton =
            Button(this)

        connectButton.text =
            "🔗 CONNECT"

        connectButton.isEnabled =
            false

        connectButton.setOnClickListener {

            connectToSelectedDevice()
        }

        layout.addView(connectButton)

        dragonButton =
            Button(this)

        dragonButton.text =
            "🐉 SEND DRAGON"

        dragonButton.isEnabled =
            false

        dragonButton.setOnClickListener {

            sendResult("DRAGON")
        }

        layout.addView(dragonButton)

        tigerButton =
            Button(this)

        tigerButton.text =
            "🐯 SEND TIGER"

        tigerButton.isEnabled =
            false

        tigerButton.setOnClickListener {

            sendResult("TIGER")
        }

        layout.addView(tigerButton)

        setContentView(layout)
    }

    private fun requestPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.CAMERA
                ),
                REQUEST_PERMISSIONS
            )

        } else {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                REQUEST_PERMISSIONS
            )
        }
    }

    private fun scanQR() {

        try {

            val integrator =
                IntentIntegrator(this)

            integrator.setDesiredBarcodeFormats(
                IntentIntegrator.QR_CODE
            )

            integrator.setPrompt(
                "Analyzer QR को Scan करें"
            )

            integrator.setBeepEnabled(true)

            integrator.setOrientationLocked(false)

            integrator.initiateScan()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Scanner error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        val result: IntentResult? =
            IntentIntegrator.parseActivityResult(
                requestCode,
                resultCode,
                data
            )

        if (result != null) {

            if (result.contents == null) {

                statusText.text =
                    "QR Scan Cancelled"

            } else {

                processQR(
                    result.contents
                )
            }

            return
        }

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
    }

    private fun processQR(
        qrData: String
    ) {

        val parts =
            qrData.split("|")

        if (parts.size < 2) {

            statusText.text =
                "Invalid Analyzer QR"

            return
        }

        if (
            parts[0] !=
            "DRAGON_TIGER_ANALYZER"
        ) {

            statusText.text =
                "This is not a Dragon Tiger Analyzer QR"

            return
        }

        /*
         * IMPORTANT:
         *
         * No MAC address is read.
         *
         * QR only confirms that this is
         * our Analyzer application.
         */

        statusText.text =
            "Analyzer QR detected.\nSearching paired devices..."

        findPairedDevices()
    }

    private fun findPairedDevices() {

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            statusText.text =
                "Bluetooth not supported"

            return
        }

        if (!adapter.isEnabled) {

            statusText.text =
                "Please turn Bluetooth ON"

            return
        }

        try {

            val devices =
                adapter.bondedDevices.toList()

            if (devices.isEmpty()) {

                statusText.text =
                    "No paired Bluetooth device found.\n" +
                    "Pair Analyzer phone first."

                return
            }

            showPairedDevices(
                devices
            )

        } catch (e: SecurityException) {

            statusText.text =
                "Bluetooth permission required"

        } catch (e: Exception) {

            statusText.text =
                "Device search error: ${e.message}"
        }
    }

    private fun showPairedDevices(
        devices: List<BluetoothDevice>
    ) {

        val names =
            devices.map {

                try {

                    it.name ?: "Unknown Device"

                } catch (e: Exception) {

                    "Bluetooth Device"
                }
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(
                "Select Analyzer Phone"
            )
            .setItems(names) { _, which ->

                selectedDevice =
                    devices[which]

                val name =
                    try {

                        selectedDevice?.name
                            ?: "Unknown"

                    } catch (e: Exception) {

                        "Bluetooth Device"
                    }

                statusText.text =
                    "Selected Analyzer:\n$name"

                connectButton.isEnabled =
                    true
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun connectToSelectedDevice() {

        val device =
            selectedDevice

        if (device == null) {

            Toast.makeText(
                this,
                "पहले Analyzer device select करें",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        connectButton.isEnabled =
            false

        statusText.text =
            "Connecting..."

        thread {

            var socket: BluetoothSocket? =
                null

            try {

                /*
                 * First try secure RFCOMM.
                 */

                socket =
                    device.createRfcommSocketToServiceRecord(
                        SERVICE_UUID
                    )

                bluetoothSocket =
                    socket

                socket.connect()

                outputStream =
                    socket.outputStream

                runOnUiThread {

                    onConnected()
                }

            } catch (e: Exception) {

                /*
                 * If secure RFCOMM fails,
                 * try insecure RFCOMM.
                 */

                try {

                    socket?.close()

                } catch (_: Exception) {
                }

                try {

                    socket =
                        device.createInsecureRfcommSocketToServiceRecord(
                            SERVICE_UUID
                        )

                    bluetoothSocket =
                        socket

                    socket.connect()

                    outputStream =
                        socket.outputStream

                    runOnUiThread {

                        onConnected()
                    }

                } catch (e2: Exception) {

                    try {

                        socket?.close()

                    } catch (_: Exception) {
                    }

                    runOnUiThread {

                        connectButton.isEnabled =
                            true

                        statusText.text =
                            "Connection failed:\n" +
                            "${e2.message}"
                    }
                }
            }
        }
    }

    private fun onConnected() {

        statusText.text =
            "✅ Analyzer Connected"

        dragonButton.isEnabled =
            true

        tigerButton.isEnabled =
            true

        connectButton.isEnabled =
            false

        Toast.makeText(
            this,
            "Connected Successfully",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun sendResult(
        result: String
    ) {

        val stream =
            outputStream

        if (stream == null) {

            Toast.makeText(
                this,
                "Analyzer connected नहीं है",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        thread {

            try {

                val message =
                    "$result\n"

                stream.write(
                    message.toByteArray(
                        Charsets.UTF_8
                    )
                )

                stream.flush()

                runOnUiThread {

                    statusText.text =
                        "Sent: $result"
                }

            } catch (e: IOException) {

                runOnUiThread {

                    statusText.text =
                        "Send failed: ${e.message}"

                    dragonButton.isEnabled =
                        false

                    tigerButton.isEnabled =
                        false
                }
            }
        }
    }

    override fun onDestroy() {

        try {

            outputStream?.close()

        } catch (_: Exception) {
        }

        try {

            bluetoothSocket?.close()

        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
