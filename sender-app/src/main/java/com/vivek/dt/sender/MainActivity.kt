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

    companion object {
        private const val REQUEST_PERMISSIONS = 1001

        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        createUI()

        requestRequiredPermissions()
    }

    private fun createUI() {

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 50, 40, 40)

        val title = TextView(this)
        title.text = "🐉 DRAGON TIGER SENDER"
        title.textSize = 25f

        val subtitle = TextView(this)
        subtitle.text = "Game Result Sender"
        subtitle.textSize = 17f

        statusText = TextView(this)
        statusText.text = "🔴 Bluetooth: Not Connected"
        statusText.textSize = 18f
        statusText.setPadding(0, 30, 0, 20)

        deviceText = TextView(this)
        deviceText.text = "Analyzer: Not Selected"
        deviceText.textSize = 16f

        scanButton = Button(this)
        scanButton.text = "📷 SCAN ANALYZER QR"

        connectButton = Button(this)
        connectButton.text = "🔵 CONNECT BLUETOOTH"

        dragonButton = Button(this)
        dragonButton.text = "🐉 DRAGON"

        tigerButton = Button(this)
        tigerButton.text = "🐯 TIGER"

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

    private fun requestRequiredPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.CAMERA
            )

            val missing = permissions.filter {
                ActivityCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {

                ActivityCompat.requestPermissions(
                    this,
                    missing.toTypedArray(),
                    REQUEST_PERMISSIONS
                )
            }

        } else {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_PERMISSIONS
                )
            }
        }
    }

    private fun startQRScanner() {

        try {

            val integrator = IntentIntegrator(this)

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

                val qrData = result.contents

                processQRData(qrData)

            } else {

                showToast("QR scan cancelled")
            }

            return
        }

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
    }

    private fun processQRData(qrData: String) {

        /*
         * Expected QR format:
         *
         * DRAGON_TIGER_ANALYZER|NAME|MAC_ADDRESS
         *
         * Example:
         *
         * DRAGON_TIGER_ANALYZER|VIVEK-ANALYZER|AA:BB:CC:DD:EE:FF
         */

        try {

            val parts = qrData.split("|")

            if (parts.size >= 3 &&
                parts[0] == "DRAGON_TIGER_ANALYZER"
            ) {

                analyzerName = parts[1]
                analyzerAddress = parts[2]

                deviceText.text =
                    "Analyzer: $analyzerName\nMAC: $analyzerAddress"

                statusText.text =
                    "🟡 Analyzer selected — Connect करें"

                showToast(
                    "Analyzer QR successfully scanned"
                )

            } else {

                showToast(
                    "Invalid Analyzer QR Code"
                )
            }

        } catch (e: Exception) {

            showToast(
                "QR data error: ${e.message}"
            )
        }
    }

    private fun connectToAnalyzer() {

        if (analyzerAddress == null) {

            showToast(
                "पहले Analyzer का QR scan करें"
            )

            return
        }

        if (bluetoothAdapter == null) {

            showToast(
                "इस फोन में Bluetooth उपलब्ध नहीं है"
            )

            return
        }

        if (!bluetoothAdapter!!.isEnabled) {

            val intent =
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            startActivity(intent)

            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

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

        thread {

            try {

                bluetoothDevice =
                    bluetoothAdapter!!.getRemoteDevice(
                        analyzerAddress
                    )

                bluetoothAdapter!!.cancelDiscovery()

                bluetoothSocket =
                    bluetoothDevice!!.createRfcommSocketToServiceRecord(
                        SPP_UUID
                    )

                runOnUiThread {

                    statusText.text =
                        "🟡 Connecting..."
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
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🔴 Connection Failed"

                    showToast(
                        "Connection failed: ${e.message}"
                    )
                }

                closeConnection()
            }
        }
    }

    private fun sendResult(result: String) {

        if (outputStream == null) {

            showToast(
                "पहले Analyzer से Bluetooth connect करें"
            )

            return
        }

        thread {

            try {

                /*
                 * Data format sent to Analyzer:
                 *
                 * DRAGON
                 * TIGER
                 */

                val message =
                    "$result\n"

                outputStream!!.write(
                    message.toByteArray(
                        Charsets.UTF_8
                    )
                )

                outputStream!!.flush()

                runOnUiThread {

                    showToast(
                        "$result भेज दिया गया"
                    )
                }

            } catch (e: IOException) {

                runOnUiThread {

                    statusText.text =
                        "🔴 Connection Lost"

                    showToast(
                        "Send failed: ${e.message}"
                    )
                }

                closeConnection()
            }
        }
    }

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

    private fun showToast(message: String) {

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
