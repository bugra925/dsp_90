package app.dsp90

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var am: AudioManager
    private var vis: Visualizer? = null
    private val h = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            js("onPlaying(" + am.isMusicActive + ")")
            h.postDelayed(this, 1000)
        }
    }

    private val devCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>?) { pushDevices() }
        override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>?) { pushDevices() }
    }

    inner class Bridge {
        @JavascriptInterface fun setMode(m: String) { Engine.mode = m; Engine.update() }
        @JavascriptInterface fun setAmount(a: Int) { Engine.amount = a; Engine.update() }
        @JavascriptInterface fun setEnabled(e: Boolean) { Engine.enabled = e; Engine.update() }
        @JavascriptInterface fun devices(): String = deviceJson()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        am = getSystemService(AudioManager::class.java)
        web = WebView(this)
        web.setBackgroundColor(0)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)

        val need = ArrayList<String>()
        need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) need.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(need.toTypedArray(), 1)
        startForegroundService(Intent(this, EffectService::class.java))
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        startVis()
        pushDevices()
    }

    override fun onResume() {
        super.onResume()
        startVis()
        am.registerAudioDeviceCallback(devCb, null)
        pushDevices()
        h.post(poll)
    }

    override fun onPause() {
        h.removeCallbacks(poll)
        am.unregisterAudioDeviceCallback(devCb)
        try { vis?.setEnabled(false); vis?.release() } catch (_: Throwable) {}
        vis = null
        super.onPause()
    }

    private fun js(c: String) { web.evaluateJavascript(c, null) }

    private fun startVis() {
        if (vis != null || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            val v = Visualizer(0)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, w: ByteArray?, sr: Int) {}
                override fun onFftDataCapture(vz: Visualizer?, f: ByteArray?, sr: Int) { if (f != null) sendFft(f) }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.setEnabled(true)
            vis = v
        } catch (e: Throwable) {
            js("onStatus('Spektrum bu telefonda kullanılamıyor')")
        }
    }

    private fun sendFft(f: ByteArray) {
        val n = f.size / 2
        val out = FloatArray(24)
        for (i in 0 until 24) {
            val a = 1 + ((i / 24f).pow(2) * (n - 2)).toInt()
            val b = max(a + 1, 1 + (((i + 1) / 24f).pow(2) * (n - 2)).toInt())
            var m = 0f
            for (k in a until min(b, n)) m = max(m, hypot(f[2 * k].toFloat(), f[2 * k + 1].toFloat()))
            out[i] = (ln(m + 1f) / ln(150f)).coerceIn(0f, 1f)
        }
        val s = out.joinToString(",") { String.format(Locale.US, "%.2f", it) }
        runOnUiThread { js("onFft([$s])") }
    }

    private fun deviceJson(): String {
        val names = mapOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP to "BT",
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO to "BT",
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "YERLEŞİK",
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES to "KABLO",
            AudioDeviceInfo.TYPE_WIRED_HEADSET to "KABLO",
            AudioDeviceInfo.TYPE_USB_DEVICE to "USB",
            AudioDeviceInfo.TYPE_USB_HEADSET to "USB"
        )
        val shown = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { names.containsKey(it.type) }
        val active = shown.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: shown.firstOrNull()
        val arr = JSONArray()
        for (d in shown) {
            arr.put(JSONObject().put("n", d.productName?.toString() ?: "Cihaz").put("t", names[d.type]).put("a", d.id == active?.id))
        }
        return arr.toString()
    }

    private fun pushDevices() { js("onDevices(" + deviceJson() + ")") }
}
