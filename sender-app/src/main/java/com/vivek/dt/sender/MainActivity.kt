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

        private val SPP_UUID: UUID =
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

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val layout = LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            40,
            50,
            40,
            40
        )

        val title = TextView(this)

        title.text =
            "🐉 DRAGON TIGER SENDER"

        title.textSize = 25f

        val subtitle = TextView(this)

        subtitle.text =
            "Game Result Sender"

        subtitle.textSize = 17f

        statusText = TextView(this)

        statusText.text =
            "🔴 Bluetooth: Not Connected"

        statusText.textSize = 18f

        statusText.setPadding(
            0,
            30,
            0,
            20
        )

        deviceText = TextView(this)

        deviceText.text =
            "Analyzer: Not Selected"

        deviceText.textSize = 16f

        deviceText.setPadding(
            0,
            10,
            0,
            20
        )

        scanButton = Button(this)

        scanButton.text =
            "📷 SCAN ANALYZER QR"

        connectButton = Button(this)

        connectButton.text =
            "🔵 CONNECT BLUETOOTH"

        dragonButton = Button(this)

        dragonButton.text =
            "🐉 DRAGON"

        tigerButton = Button(this)

        tigerButton.text =
            "🐯 TIGER"

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

        if (Build.VERSION.SDK_INT >=
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

        val missingPermissions =
            permissions.filter {

                ActivityCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    // =========================================================
    // QR SCANNER
    // =========================================================

    private fun startQRScanner() {

        try {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestRequiredPermissions()

                showToast(
                    "Camera permission required"
                )

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
                "QR Scanner error: ${e.message}"
            )
        }
    }

    // =========================================================
    // QR RESULT
    // =========================================================

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

            if (result.contents != null) {

                processQRData(
                    result.contents
                )

            } else {

                showToast(
                    "QR scan cancelled"
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
    // PROCESS ANALYZER QR
    // =========================================================

    private fun processQRData(
        qrData: String
    ) {

        try {

            val data =
                qrData.trim()

            val parts =
                data.split("|")

            /*
             * Expected:
             *
             * DRAGON_TIGER_ANALYZER|NAME|MAC
             *
             * Example:
             *
             * DRAGON_TIGER_ANALYZER|
             * DragonTiger-Analyzer|
             * AA:BB:CC:DD:EE:FF
             */

            if (
                parts.size >= 3 &&
                parts[0]
                    .trim()
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
                    name.isBlank()
                ) {

                    showToast(
                        "Analyzer name missing"
                    )

                    return
                }

                if (
                    mac.isBlank()
                ) {

                    showToast(
                        "Bluetooth MAC address missing"
                    )

                    deviceText.text =
                        "Analyzer: $name\n" +
                        "MAC: Not available"

                    return
                }

                if (
                    !isBluetoothMac(mac)
                ) {

                    showToast(
                        "Invalid Bluetooth MAC: $mac"
                    )

                    deviceText.text =
                        "QR Data:\n$data"

                    return
                }

                analyzerName =
                    name

                analyzerAddress =
                    mac

                deviceText.text =
                    "Analyzer: $name\n" +
                    "MAC: $mac"

                statusText.text =
                    "🟡 Analyzer selected\n" +
                    "अब CONNECT BLUETOOTH दबाएँ"

                showToast(
                    "Analyzer QR successfully scanned"
                )

                return
            }

            /*
             * Fallback:
             *
             * NAME|MAC
             */

            if (
                parts.size >= 2 &&
                isBluetoothMac(
                    parts.last().trim()
                )
            ) {

                analyzerName =
                    parts.dropLast(1)
                        .joinToString("|")
                        .trim()

                analyzerAddress =
                    parts.last().trim()

                if (
                    analyzerName.isNullOrBlank()
                ) {

                    analyzerName =
                        "Analyzer"
                }

                deviceText.text =
                    "Analyzer: $analyzerName\n" +
                    "MAC: $analyzerAddress"

                statusText.text =
                    "🟡 Analyzer selected\n" +
                    "अब CONNECT BLUETOOTH दबाएँ"

                showToast(
                    "Analyzer QR successfully scanned"
                )

                return
            }

            /*
             * Invalid QR
             */

            deviceText.text =
                "QR Data:\n$data"

            statusText.text =
                "🔴 Invalid Analyzer QR"

            showToast(
                "Invalid Analyzer QR Code"
            )

        } catch (e: Exception) {

            showToast(
                "QR data error: ${e.message}"
            )
        }
    }

    // =========================================================
    // MAC VALIDATION
    // =========================================================

    private fun isBluetoothMac(
        value: String
    ): Boolean {

        val macPattern =
            Regex(
                "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"
            )

        return macPattern.matches(
            value
        )
    }

    // =========================================================
    // BLUETOOTH CONNECTION
    // =========================================================

    private fun connectToAnalyzer() {

        val address =
            analyzerAddress

        if (address.isNullOrBlank()) {

            showToast(
                "पहले Analyzer का QR scan करें"
            )

            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            showToast(
                "इस phone में Bluetooth उपलब्ध नहीं है"
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
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestRequiredPermissions()

                return
            }
        }

        try {

            if (!adapter.isEnabled) {

                val intent =
                    Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE
                    )

                startActivity(intent)

                showToast(
                    "Bluetooth ON करें"
                )

                return
            }

        } catch (e: SecurityException) {

            showToast(
                "Bluetooth permission required"
            )

            return
        }

        statusText.text =
            "🟡 Connecting..."

        thread {

            try {

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

                        runOnUiThread {

                            statusText.text =
                                "🔐 Bluetooth permission required"

                            requestRequiredPermissions()
                        }

                        return@thread
                    }
                }

                bluetoothDevice =
                    adapter.getRemoteDevice(
                        address
                    )

                try {

                    adapter.cancelDiscovery()

                } catch (_: SecurityException) {
                }

                bluetoothSocket =
                    bluetoothDevice!!
                        .createRfcommSocketToServiceRecord(
                            SPP_UUID
                        )

                runOnUiThread {

                    statusText.text =
                        "🟡 Connecting to Analyzer..."
                }

                bluetoothSocket!!.connect()

                outputStream =
                    bluetoothSocket!!.outputStream

                runOnUiThread {

                    statusText.text =
                        "🟢 Bluetooth Connected"

                    showToast(
                        "Analyzer connected successfully"
                    )

                    enableResultButtons(
                        true
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🔴 Connection Failed"

                    showToast(
                        "Connection failed:\n${e.message}"
                    )

                    enableResultButtons(
                        false
                    )
                }

                closeConnection()
            }
        }
    }

    // =========================================================
    // ENABLE / DISABLE RESULT BUTTONS
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
    // SEND RESULT
    // =========================================================

    private fun sendResult(
        result: String
    ) {

        val stream =
            outputStream

        if (stream == null) {

            showToast(
                "पहले Analyzer से Bluetooth connect करें"
            )

            return
        }

        thread {

            try {

                /*
                 * Analyzer accepts:
                 *
                 * DRAGON
                 * TIGER
                 *
                 * Every result ends with \n
                 * because Analyzer uses readLine().
                 */

                val message =
                    "$result\n"

                stream.write(
                    message.toByteArray(
                        Charsets.UTF_8
                    )
                )

                stream.flush()

                runOnUiThread {

                    if (
                        result.equals(
                            "DRAGON",
                            ignoreCase = true
                        )
                    ) {

                        showToast(
                            "🐉 DRAGON भेज दिया गया"
                        )

                    } else {

                        showToast(
                            "🐯 TIGER भेज दिया गया"
                        )
                    }
                }

            } catch (e: IOException) {

                runOnUiThread {

                    statusText.text =
                        "🔴 Connection Lost"

                    showToast(
                        "Send failed:\n${e.message}"
                    )

                    enableResultButtons(
                        false
                    )
                }

                closeConnection()
            }
        }
    }

    // =========================================================
    // CLOSE CONNECTION
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

    // =========================================================
    // TOAST
    // =========================================================

    private fun showToast(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        closeConnection()

        super.onDestroy()
    }
}
