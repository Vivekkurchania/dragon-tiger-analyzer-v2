package com.vivek.dt.analyzer

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {

        private const val REQUEST_BLUETOOTH = 1001

        private const val SERVICE_NAME =
            "Dragon Tiger Analyzer"

        private val SERVICE_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }

    private lateinit var statusText: TextView
    private lateinit var qrImage: ImageView
    private lateinit var resultText: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var serverSocket: BluetoothServerSocket? = null

    private val results =
        ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter()

        createUI()

        requestBluetoothPermission()

        showAnalyzerQR()

        startBluetoothServer()
    }

    // =========================================================
    // PERMISSION
    // =========================================================

    private fun requestBluetoothPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                REQUEST_BLUETOOTH
            )
        }
    }

    // =========================================================
    // UI
    // =========================================================

    private fun createUI() {

        val scrollView = ScrollView(this)

        val layout = LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            30,
            30,
            30,
            30
        )

        val title = TextView(this)

        title.text =
            "📊 DRAGON TIGER ANALYZER"

        title.textSize = 25f

        statusText = TextView(this)

        statusText.text =
            "🔵 Starting Analyzer..."

        statusText.textSize = 18f

        statusText.setPadding(
            0,
            15,
            0,
            20
        )

        val qrTitle = TextView(this)

        qrTitle.text =
            "📱 इस QR को Sender phone से scan करें"

        qrTitle.textSize = 18f

        qrImage = ImageView(this)

        qrImage.adjustViewBounds = true

        qrImage.minimumHeight = 500

        val refreshButton = Button(this)

        refreshButton.text =
            "🔄 REFRESH QR"

        refreshButton.setOnClickListener {
            showAnalyzerQR()
        }

        resultText = TextView(this)

        resultText.text =
            """
            Latest Results: 0/100
            
            Dragon: 0
            Tiger: 0
            
            Last Result: —
            
            NEXT STATISTICAL ESTIMATE
            —
            """.trimIndent()

        resultText.textSize = 19f

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(qrTitle)
        layout.addView(qrImage)
        layout.addView(refreshButton)
        layout.addView(resultText)

        scrollView.addView(layout)

        setContentView(scrollView)
    }

    // =========================================================
    // QR
    // =========================================================

    private fun showAnalyzerQR() {

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            statusText.text =
                "❌ Bluetooth unavailable"

            return
        }

        try {

            val deviceName =
                try {
                    adapter.name
                        ?: "DragonTiger-Analyzer"
                } catch (_: SecurityException) {
                    "DragonTiger-Analyzer"
                }

            val address =
                try {
                    adapter.address
                } catch (_: SecurityException) {
                    ""
                }

            val payload =
                "DRAGON_TIGER_ANALYZER|$deviceName|$address"

            val matrix =
                MultiFormatWriter().encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    700,
                    700
                )

            val bitmap =
                createBitmapFromMatrix(
                    matrix,
                    700,
                    700
                )

            qrImage.setImageBitmap(bitmap)

            statusText.text =
                """
                🟢 QR READY
                
                Analyzer:
                $deviceName
                
                Bluetooth:
                ${if (address.isBlank()) "Address unavailable" else address}
                
                Sender से QR scan करें।
                """.trimIndent()

        } catch (e: Exception) {

            statusText.text =
                "❌ QR Error: ${e.message}"
        }
    }

    private fun createBitmapFromMatrix(
        matrix: BitMatrix,
        width: Int,
        height: Int
    ): Bitmap {

        val pixels =
            IntArray(width * height)

        for (y in 0 until height) {

            for (x in 0 until width) {

                pixels[y * width + x] =
                    if (matrix[x, y]) {
                        0xFF000000.toInt()
                    } else {
                        0xFFFFFFFF.toInt()
                    }
            }
        }

        return Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }

    // =========================================================
    // BLUETOOTH SERVER
    // =========================================================

    private fun startBluetoothServer() {

        thread {

            try {

                val adapter =
                    bluetoothAdapter
                        ?: throw Exception(
                            "Bluetooth unavailable"
                        )

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S
                ) {

                    if (
                        checkSelfPermission(
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        runOnUiThread {
                            statusText.text =
                                "🔐 Bluetooth permission required"
                        }

                        return@thread
                    }
                }

                // Close old server if any
                try {
                    serverSocket?.close()
                } catch (_: Exception) {
                }

                /*
                 * Secure RFCOMM server.
                 */
                serverSocket =
                    adapter.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME,
                        SERVICE_UUID
                    )

                runOnUiThread {

                    statusText.text =
                        "🟢 READY — Sender का इंतजार..."
                }

                while (true) {

                    try {

                        val socket =
                            serverSocket?.accept()
                                ?: break

                        runOnUiThread {

                            statusText.text =
                                "🟢 SENDER CONNECTED"
                        }

                        handleConnection(socket)

                    } catch (e: Exception) {

                        if (!isFinishing) {

                            runOnUiThread {

                                statusText.text =
                                    "🟡 Waiting for Sender..."
                            }
                        }

                        // Continue listening
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "❌ Bluetooth Server Error\n${e.message}"
                }
            }
        }
    }

    // =========================================================
    // CONNECTION
    // =========================================================

    private fun handleConnection(
        socket: BluetoothSocket
    ) {

        thread {

            try {

                val input =
                    socket.inputStream

                val buffer =
                    ByteArray(1024)

                val textBuffer =
                    StringBuilder()

                while (true) {

                    val count =
                        input.read(buffer)

                    if (count <= 0) {
                        break
                    }

                    val text =
                        String(
                            buffer,
                            0,
                            count,
                            Charsets.UTF_8
                        )

                    textBuffer.append(text)

                    var newlineIndex =
                        textBuffer.indexOf("\n")

                    while (newlineIndex >= 0) {

                        val message =
                            textBuffer
                                .substring(
                                    0,
                                    newlineIndex
                                )
                                .trim()

                        textBuffer.delete(
                            0,
                            newlineIndex + 1
                        )

                        if (message.isNotEmpty()) {

                            processReceivedResult(
                                message
                            )
                        }

                        newlineIndex =
                            textBuffer.indexOf("\n")
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🟡 Sender disconnected\nWaiting..."
                }

            } finally {

                try {
                    socket.close()
                } catch (_: Exception) {
                }

                // Server remains available for
                // the next Sender connection.
            }
        }
    }

    // =========================================================
    // RESULT
    // =========================================================

    private fun processReceivedResult(
        message: String
    ) {

        val result =
            when {

                message.equals(
                    "DRAGON",
                    ignoreCase = true
                ) -> "D"

                message.equals(
                    "D",
                    ignoreCase = true
                ) -> "D"

                message.equals(
                    "TIGER",
                    ignoreCase = true
                ) -> "T"

                message.equals(
                    "T",
                    ignoreCase = true
                ) -> "T"

                else -> null
            }

        if (result == null) {
            return
        }

        synchronized(results) {

            if (results.size >= 100) {
                results.removeFirst()
            }

            results.addLast(result)
        }

        runOnUiThread {
            updateAnalysis()
        }
    }

    // =========================================================
    // ANALYSIS
    // =========================================================

    private fun updateAnalysis() {

        val data =
            synchronized(results) {
                results.toList()
            }

        if (data.isEmpty()) {

            resultText.text =
                """
                Latest Results: 0/100
                
                Dragon: 0
                Tiger: 0
                
                Last Result: —
                
                NEXT STATISTICAL ESTIMATE
                —
                """.trimIndent()

            return
        }

        val dragonCount =
            data.count { it == "D" }

        val tigerCount =
            data.count { it == "T" }

        val last =
            data.last()

        var dd = 0
        var dt = 0
        var td = 0
        var tt = 0

        for (i in 1 until data.size) {

            when (
                data[i - 1] + data[i]
            ) {

                "DD" -> dd++
                "DT" -> dt++
                "TD" -> td++
                "TT" -> tt++
            }
        }

        val transitionDragon =
            if (last == "D") {

                (dd + 1.0) /
                    (dd + dt + 2.0)

            } else {

                (td + 1.0) /
                    (td + tt + 2.0)
            }

        val frequencyDragon =
            (dragonCount + 1.0) /
                (data.size + 2.0)

        val score =
            transitionDragon * 0.60 +
            frequencyDragon * 0.40

        val estimate =
            if (score >= 0.50) {
                "🐉 DRAGON"
            } else {
                "🐯 TIGER"
            }

        val scorePercent =
            (score * 100).toInt()

        resultText.text =
            """
            📊 ANALYSIS
            
            Latest Results:
            ${data.size}/100
            
            🐉 Dragon: $dragonCount
            🐯 Tiger: $tigerCount
            
            Last Result:
            ${if (last == "D") "🐉 DRAGON" else "🐯 TIGER"}
            
            ----------------------------
            
            TRANSITIONS
            
            DD: $dd
            DT: $dt
            TD: $td
            TT: $tt
            
            ----------------------------
            
            NEXT STATISTICAL ESTIMATE
            
            $estimate
            
            Statistical Score:
            $scorePercent%
            
            ----------------------------
            
            ⚠️ यह historical/statistical
            analysis है।
            
            RNG outcome guaranteed
            predict नहीं किया जा सकता।
            """.trimIndent()
    }

    override fun onDestroy() {

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null

        super.onDestroy()
    }
}
