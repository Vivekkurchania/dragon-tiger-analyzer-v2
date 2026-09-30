package com.vivek.dt.analyzer

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.os.Bundle
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val data = ArrayDeque<String>()
    private lateinit var status: TextView
    private lateinit var view: TextView
    private lateinit var qr: ImageView
    private val adapter by lazy { BluetoothAdapter.getDefaultAdapter() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 110)
        buildUi(); startServer()
    }

    private fun buildUi() {
        val r = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val h = TextView(this).apply { text = "📊 Dragon Tiger Analyzer v2"; textSize = 25f }
        status = TextView(this).apply { text = "Waiting for Sender…"; textSize = 17f; setPadding(0,14,0,14) }
        val qrBtn = Button(this).apply { text = "🔳 Refresh / Show Bluetooth QR" }
        qr = ImageView(this).apply { adjustViewBounds = true; minimumHeight = 500 }
        view = TextView(this).apply { text = "Latest 100: 0\n\nNext statistical estimate: —"; textSize = 20f; setPadding(0,14,0,14) }
        box.addView(h); box.addView(status); box.addView(qrBtn); box.addView(qr); box.addView(view); r.addView(box); setContentView(r)
        qrBtn.setOnClickListener { showQr() }
        showQr()
    }

    private fun showQr() {
        val bt = adapter ?: return
        val name = try { bt.name ?: "Analyzer" } catch (_: Exception) { "Analyzer" }
        val address = try { bt.address } catch (_: Exception) { "" }
        if (address.isBlank()) { status.text = "Bluetooth address unavailable"; return }
        val payload = "DTBT|$name|$address"
        try {
            val matrix: BitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 650, 650)
            val pixels = IntArray(650 * 650) { i -> if (matrix[i % 650, i / 650]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            qr.setImageBitmap(android.graphics.Bitmap.createBitmap(pixels, 650, 650, android.graphics.Bitmap.Config.ARGB_8888))
            status.text = "Scan this QR from Sender. Pair phones in Bluetooth Settings first."
        } catch (e: Exception) { status.text = "QR error: ${e.message}" }
    }

    private fun startServer() {
        thread {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord("DT Analyzer", uuid)
                runOnUiThread { status.text = "Waiting for Sender… QR ready" }
                while (true) {
                    val sock = server.accept()
                    runOnUiThread { status.text = "✅ Sender connected" }
                    val br = sock.inputStream.bufferedReader()
                    while (true) {
                        val x = br.readLine() ?: break
                        if (x == "D" || x == "T") add(x)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Bluetooth error: ${e.message}" }
            }
        }
    }

    private fun add(x: String) {
        synchronized(data) { if (data.size >= 100) data.removeFirst(); data.addLast(x) }
        runOnUiThread { update() }
    }

    private fun update() {
        val a = data.toList(); val d = a.count { it == "D" }; val t = a.size - d
        var dd=0; var dt=0; var td=0; var tt=0
        for (i in 1 until a.size) when (a[i-1] + a[i]) { "DD"->dd; "DT"->dt; "TD"->td; "TT"->tt }
        val last = a.lastOrNull(); val transition = when(last) { "D" -> (dd+1.0)/(dd+dt+2); "T" -> (td+1.0)/(td+tt+2); else -> .5 }
        val freq = (d+1.0)/(a.size+2); val score=.6*transition+.4*freq
        val est=if(score>=.5) "🐉 DRAGON" else "🐯 TIGER"
        view.text="Latest results: ${a.size}/100\nDragon: $d   Tiger: $t\nLast: ${last ?: "—"}\n\nNEXT STATISTICAL ESTIMATE\n$est\nScore: ${(score*100).toInt()}%\n\nStatistical estimate only — RNG outcomes cannot be guaranteed."
    }
}
