package com.vivek.dt.sender

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.core.app.ActivityCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import java.io.OutputStream
import java.util.UUID

class MainActivity : Activity() {
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var out: OutputStream? = null
    private var socket: BluetoothSocket? = null
    private lateinit var status: TextView
    private val adapter by lazy { BluetoothAdapter.getDefaultAdapter() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestBtPermissions()
        buildUi()
    }

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.CAMERA
            ), 100)
        } else if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val title = TextView(this).apply { text = "🐉 Dragon Tiger Sender v2"; textSize = 25f }
        status = TextView(this).apply { text = "Not connected"; textSize = 17f; setPadding(0,18,0,18) }
        val scan = Button(this).apply { text = "📷 Scan Analyzer QR" }
        val pair = Button(this).apply { text = "Bluetooth Settings / Pair" }
        val dragon = Button(this).apply { text = "🐉 DRAGON"; textSize = 22f }
        val tiger = Button(this).apply { text = "🐯 TIGER"; textSize = 22f }
        root.addView(title); root.addView(status); root.addView(scan); root.addView(pair); root.addView(dragon); root.addView(tiger)
        scan.setOnClickListener { startQrScan() }
        pair.setOnClickListener { startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
        dragon.setOnClickListener { send("D") }
        tiger.setOnClickListener { send("T") }
        setContentView(root)
    }

    private fun startQrScan() {
        IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Scan the Analyzer QR code").setBeepEnabled(true).setOrientationLocked(false).initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) handleQr(result.contents)
            else Toast.makeText(this, "QR scan cancelled", Toast.LENGTH_SHORT).show()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleQr(payload: String) {
        if (!payload.startsWith("DTBT|")) {
            status.text = "Invalid Analyzer QR"
            return
        }
        val p = payload.split('|')
        if (p.size < 3) return
        val name = p[1]
        val address = p[2]
        status.text = "QR read: $name\nConnecting…"
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Bluetooth permission required"
            return
        }
        val device = try { adapter.bondedDevices.firstOrNull { it.address.equals(address, true) } } catch (_: Exception) { null }
        if (device == null) {
            status.text = "Analyzer not paired yet. Pair phones in Bluetooth Settings, then scan QR again."
            Toast.makeText(this, "Pair the two phones in Bluetooth Settings first", Toast.LENGTH_LONG).show()
            return
        }
        connect(device)
    }

    private fun connect(device: BluetoothDevice) {
        Thread {
            try {
                socket?.close()
                val s = device.createRfcommSocketToServiceRecord(uuid)
                s.connect()
                socket = s
                out = s.outputStream
                runOnUiThread { status.text = "✅ Connected to ${device.name ?: device.address}" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Connection failed: ${e.message}" }
            }
        }.start()
    }

    private fun send(x: String) {
        try {
            val stream = out ?: throw IllegalStateException()
            stream.write("$x\n".toByteArray()); stream.flush()
            Toast.makeText(this, "Sent $x", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Connect to Analyzer first", Toast.LENGTH_SHORT).show()
        }
    }
}
