package com.vivek.dt.sender

import android.Manifest
import android.app.Activity
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

    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var dragonButton: Button
    private lateinit var tigerButton: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private var analyzerAddress: String? = null
    private var analyzerName: String? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        bluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter()

        createUI()

        requestRequiredPermissions()
    }

    // =========================================================
    // UI
    // =========================================================

    private fun createUI() {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            40,
            50,
            40,
            40
        )

        val title =
            TextView(this)

        title.text =
            "🐉 DRAGON TIGER SENDER"

        title.textSize =
            25f

        val subtitle =
            TextView(this)

        subtitle.text =
            "Game Result Sender"

        subtitle.textSize =
            17f

        statusText =
            TextView(this)

        statusText.text =
            "🔴 Bluetooth: Not Connected"

        statusText.textSize =
            18f

        statusText.setPadding(
            0,
            30,
            0,
            20
        )

        deviceText =
            TextView(this)

        deviceText.text =
            "Analyzer: Not Selected"

        deviceText.textSize =
            16f

        deviceText.setPadding(
            0,
            10,
            0,
            20
        )

        scanButton =
            Button(this)

        scanButton.text =
            "📷 SCAN ANALYZER QR"

        connectButton =
            Button(this)

        connectButton.text =
            "🔵 CONNECT BLUETOOTH"

        dragonButton =
            Button(this)

        dragonButton.text =
            "🐉 DRAGON"

        dragonButton.isEnabled =
            false

        tigerButton =
            Button(this)

        tigerButton.text =
            "🐯 TIGER"

        tigerButton.isEnabled =
            false

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(deviceText)
        layout.addView(scanButton)
        layout.addView(connectButton)
        layout.addView(dragonButton)
        layout.addView(tigerButton)

        setContentView(layout)

        scanButton.setOnClickListener {
            startQRScanner()
        }

        connectButton.setOnClickListener {
            connectToAnalyzer()
        }

        dragonButton.setOnClickListener {
            sendResult("DRAGON")
        }

        tigerButton.setOnClickListener {
            sendResult("TIGER")
        }
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    private fun requestRequiredPermissions() {

        val permissions =
            mutableListOf<String>()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            permissions.add(
                Manifest.permission.BLUETOOTH_SCAN
            )

            permissions.add(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }

        permissions.add(
            Manifest.permission.CAMERA
        )

        val missing =
            permissions.filter {

                ActivityCompat.checkSelfPermission(
                    this,
                    it
                ) !=
                    PackageManager.PERMISSION_GRANTED
            }

        if (missing.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    // =========================================================
    // QR
    // =========================================================

    private fun startQRScanner() {

        try {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestRequiredPermissions()

                return
            }

            val integrator =
                IntentIntegrator(this)

            integrator.setDesiredBarcodeFormats(
                IntentIntegrator.QR_CODE
            )

            integrator.setPrompt(
                "Analyzer का QR Code scan करें"
            )

            integrator.setBeepEnabled(true)

            integrator.setOrientationLocked(true)

            integrator.initiateScan()

        } catch (e: Exception) {

            showToast(
                "QR error: ${e.message}"
            )
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        val result =
            IntentIntegrator.parseActivityResult(
                requestCode,
                resultCode,
                data
            )

        if (result != null) {

            if (result.contents != null) {

                processQRData(
                    result.contents
                )

            } else {

                showToast(
                    "QR cancelled"
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

    // =========================================================
    // QR DATA
    // =========================================================

    private fun processQRData(
        qrData: String
    ) {

        try {

            val data =
                qrData.trim()

            val parts =
                data.split("|")

            if (
                parts.size >= 3 &&
                parts[0].trim()
                    .equals(
                        "DRAGON_TIGER_ANALYZER",
                        ignoreCase = true
                    )
            ) {

                val name =
                    parts[1].trim()

                val mac =
                    parts[2].trim()

                if (
                    !isBluetoothMac(mac)
                ) {

                    deviceText.text =
                        "QR Data:\n$data"

                    showToast(
                        "Invalid Bluetooth MAC"
                    )

                    return
                }

                analyzerName =
                    name.ifBlank {
                        "Analyzer"
                    }

                analyzerAddress =
                    mac

                deviceText.text =
                    "Analyzer: $analyzerName\n" +
                    "MAC: $analyzerAddress"

                statusText.text =
                    "🟡 Analyzer selected\n" +
                    "अब CONNECT दबाएँ"

                showToast(
                    "Analyzer QR scanned"
                )

                return
            }

            deviceText.text =
                "QR Data:\n$data"

            showToast(
                "Invalid Analyzer QR Code"
            )

        } catch (e: Exception) {

            showToast(
                "QR error: ${e.message}"
            )
        }
    }

    private fun isBluetoothMac(
        value: String
    ): Boolean {

        return Regex(
            "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"
        ).matches(value)
    }

    // =========================================================
    // BLUETOOTH CONNECT
    // =========================================================

    private fun connectToAnalyzer() {

        val address =
            analyzerAddress

        if (address.isNullOrBlank()) {

            showToast(
                "पहले Analyzer QR scan करें"
            )

            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            showToast(
                "Bluetooth unavailable"
            )

            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestRequiredPermissions()

                return
            }
        }

        try {

            if (!adapter.isEnabled) {

                startActivity(
                    Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE
                    )
                )

                return
            }

        } catch (e: Exception) {

            showToast(
                "Bluetooth error: ${e.message}"
            )

            return
        }

        statusText.text =
            "🟡 Connecting..."

        enableResultButtons(false)

        thread {

            var socket:
                BluetoothSocket? = null

            try {

                val device =
                    adapter.getRemoteDevice(
                        address
                    )

                bluetoothDevice =
                    device

                try {
                    adapter.cancelDiscovery()
                } catch (_: Exception) {
                }

                /*
                 * First attempt:
                 * Secure RFCOMM
                 */

                try {

                    socket =
                        device.createRfcommSocketToServiceRecord(
                            SERVICE_UUID
                        )

                    socket.connect()

                } catch (secureError: Exception) {

                    /*
                     * Secure connection failed.
                     * Try insecure RFCOMM fallback.
                     */

                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }

                    socket =
                        device.createInsecureRfcommSocketToServiceRecord(
                            SERVICE_UUID
                        )

                    socket.connect()
                }

                bluetoothSocket =
                    socket

                outputStream =
                    socket.outputStream

                runOnUiThread {

                    statusText.text =
                        "🟢 Bluetooth Connected"

                    enableResultButtons(true)

                    showToast(
                        "Analyzer connected"
                    )
                }

            } catch (e: Exception) {

                try {
                    socket?.close()
                } catch (_: Exception) {
                }

                bluetoothSocket = null
                outputStream = null

                runOnUiThread {

                    statusText.text =
                        "🔴 Connection Failed"

                    enableResultButtons(false)

                    showToast(
                        "Connection failed:\n${e.message}"
                    )
                }
            }
        }
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private fun enableResultButtons(
        enabled: Boolean
    ) {

        dragonButton.isEnabled =
            enabled

        tigerButton.isEnabled =
            enabled
    }

    // =========================================================
    // SEND
    // =========================================================

    private fun sendResult(
        result: String
    ) {

        val stream =
            outputStream

        if (stream == null) {

            showToast(
                "Bluetooth connected नहीं है"
            )

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

                    showToast(
                        if (
                            result == "DRAGON"
                        ) {
                            "🐉 DRAGON भेज दिया"
                        } else {
                            "🐯 TIGER भेज दिया"
                        }
                    )
                }

            } catch (e: IOException) {

                runOnUiThread {

                    statusText.text =
                        "🔴 Connection Lost"

                    enableResultButtons(false)

                    showToast(
                        "Send failed:\n${e.message}"
                    )
                }

                closeConnection()
            }
        }
    }

    // =========================================================
    // CLOSE
    // =========================================================

    private fun closeConnection() {

        try {
            outputStream?.close()
        } catch (_: Exception) {
        }

        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {
        }

        outputStream = null
        bluetoothSocket = null
    }

    private fun showToast(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {

        closeConnection()

        super.onDestroy()
    }
}
