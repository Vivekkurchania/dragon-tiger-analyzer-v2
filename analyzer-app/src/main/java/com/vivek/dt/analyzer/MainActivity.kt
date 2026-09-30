package com.vivek.dt.analyzer

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import android.graphics.Bitmap
import android.widget.ImageView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 2001

        private const val SERVICE_NAME = "DragonTigerAnalyzer"

        private val SERVICE_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var qrImage: ImageView

    private val results = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter()

        createUI()

        requestBluetoothPermissions()
    }

    private fun createUI() {

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 30, 30, 30)

        val title = TextView(this)
        title.text = "🐉 DRAGON TIGER ANALYZER"
        title.textSize = 24f
        title.setTextColor(Color.BLACK)

        layout.addView(title)

        statusText = TextView(this)
        statusText.text = "Status: Starting..."
        statusText.textSize = 18f
        statusText.setPadding(0, 25, 0, 25)

        layout.addView(statusText)

        val instruction = TextView(this)

        instruction.text =
            "1. इस फोन को दूसरे फोन से Bluetooth में Pair करें\n\n" +
            "2. Sender App में नीचे दिया QR Scan करें\n\n" +
            "3. Sender में paired device select करें"

        instruction.textSize = 16f

        layout.addView(instruction)

        qrImage = ImageView(this)

        layout.addView(
            qrImage,
            LinearLayout.LayoutParams(
                700,
                700
            )
        )

        val generateButton = Button(this)

        generateButton.text = "SHOW CONNECTION QR"

        generateButton.setOnClickListener {

            generateQRCode()
        }

        layout.addView(generateButton)

        resultText = TextView(this)

        resultText.text = "Results: 0"

        resultText.textSize = 18f

        resultText.setPadding(0, 30, 0, 10)

        layout.addView(resultText)

        setContentView(layout)
    }

    private fun requestBluetoothPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            ActivityCompat.requestPermissions(
                this,
                permissions,
                REQUEST_PERMISSIONS
            )

        } else {

            startAnalyzer()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_PERMISSIONS) {

            if (
                grantResults.isNotEmpty() &&
                grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {

                startAnalyzer()

            } else {

                statusText.text =
                    "Bluetooth permission required"
            }
        }
    }

    private fun startAnalyzer() {

        generateQRCode()

        startBluetoothServer()
    }

    private fun generateQRCode() {

        try {

            /*
             * IMPORTANT:
             *
             * No MAC address here.
             *
             * QR is only used to identify
             * this as a Dragon Tiger Analyzer.
             */

            val payload =
                "DRAGON_TIGER_ANALYZER|V2"

            val size = 700

            val bitMatrix: BitMatrix =
                MultiFormatWriter().encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    size,
                    size
                )

            val bitmap =
                Bitmap.createBitmap(
                    size,
                    size,
                    Bitmap.Config.RGB_565
                )

            for (x in 0 until size) {

                for (y in 0 until size) {

                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y])
                            Color.BLACK
                        else
                            Color.WHITE
                    )
                }
            }

            qrImage.setImageBitmap(bitmap)

            statusText.text =
                "QR Ready - Bluetooth Pairing Required"

        } catch (e: Exception) {

            statusText.text =
                "QR Error: ${e.message}"
        }
    }

    private fun startBluetoothServer() {

        val adapter = bluetoothAdapter

        if (adapter == null) {

            statusText.text =
                "Bluetooth not supported"

            return
        }

        try {

            if (!adapter.isEnabled) {

                statusText.text =
                    "Please turn Bluetooth ON"

                return
            }

            thread {

                try {

                    serverSocket =
                        adapter.listenUsingRfcommWithServiceRecord(
                            SERVICE_NAME,
                            SERVICE_UUID
                        )

                    runOnUiThread {

                        statusText.text =
                            "Waiting for Sender connection..."
                    }

                    while (true) {

                        val socket =
                            serverSocket?.accept()

                        if (socket != null) {

                            clientSocket = socket

                            runOnUiThread {

                                statusText.text =
                                    "✅ Sender Connected: " +
                                    (try {
                                        socket.remoteDevice.name
                                    } catch (e: Exception) {
                                        "Unknown"
                                    })
                            }

                            handleClient(socket)
                        }
                    }

                } catch (e: IOException) {

                    runOnUiThread {

                        statusText.text =
                            "Bluetooth Server Error: ${e.message}"
                    }
                }
            }

        } catch (e: Exception) {

            statusText.text =
                "Server Error: ${e.message}"
        }
    }

    private fun handleClient(
        socket: BluetoothSocket
    ) {

        thread {

            try {

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            socket.inputStream
                        )
                    )

                while (true) {

                    val line =
                        reader.readLine()
                            ?: break

                    val value =
                        line.trim().uppercase()

                    if (value == "DRAGON" ||
                        value == "TIGER" ||
                        value == "D" ||
                        value == "T"
                    ) {

                        processResult(value)
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "Connection closed"
                }
            }
        }
    }

    private fun processResult(
        value: String
    ) {

        val result =
            when (value) {

                "D" -> "DRAGON"

                "T" -> "TIGER"

                else -> value
            }

        results.add(result)

        if (results.size > 100) {

            results.removeAt(0)
        }

        runOnUiThread {

            updateAnalysis()
        }
    }

    private fun updateAnalysis() {

        val total = results.size

        val dragon =
            results.count {
                it == "DRAGON"
            }

        val tiger =
            results.count {
                it == "TIGER"
            }

        val dragonPercent =
            if (total > 0)
                dragon * 100.0 / total
            else 0.0

        val tigerPercent =
            if (total > 0)
                tiger * 100.0 / total
            else 0.0

        val last =
            if (results.isNotEmpty())
                results.last()
            else
                "-"

        resultText.text =
            """
            Results: $total
            
            🐉 Dragon: $dragon
            📊 Dragon %: %.2f%%
            
            🐯 Tiger: $tiger
            📊 Tiger %: %.2f%%
            
            Last Result: $last
            
            Statistical analysis only.
            No guaranteed prediction.
            """.trimIndent()
                .format(
                    dragonPercent,
                    tigerPercent
                )
    }

    override fun onDestroy() {

        try {
            clientSocket?.close()
        } catch (_: Exception) {
        }

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
