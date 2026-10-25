package app.dsp90

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer

object Engine {
    private class M(val low: Int, val mid: Int, val high: Int, val reverb: Short?, val bass: Int, val virt: Int)

    private val modes = mapOf(
        "off" to M(0, 0, 0, null, 0, 0),
        "hall" to M(2, 0, -1, PresetReverb.PRESET_MEDIUMHALL, 0, 0),
        "concert" to M(1, 0, 2, PresetReverb.PRESET_LARGEHALL, 0, 300),
        "jazz" to M(3, 2, -3, PresetReverb.PRESET_MEDIUMROOM, 0, 0),
        "stadium" to M(3, 1, 1, PresetReverb.PRESET_LARGEHALL, 400, 600),
        "dome" to M(1, 0, 3, PresetReverb.PRESET_PLATE, 0, 700),
        "church" to M(1, -1, -1, PresetReverb.PRESET_LARGEHALL, 0, 200)
    )

    private class Fx(val eq: Equalizer?, val bass: BassBoost?, val virt: Virtualizer?, val rev: PresetReverb?) {
        fun release() {
            for (e in listOf<AudioEffect?>(eq, bass, virt, rev)) {
                try { e?.release() } catch (_: Throwable) {}
            }
        }
    }

    private val sets = HashMap<Int, Fx>()
    @Volatile var mode = "off"
    @Volatile var amount = 50
    @Volatile var enabled = true

    private fun <T> mk(f: () -> T): T? = try { f() } catch (_: Throwable) { null }

    @Synchronized fun attach(session: Int) {
        if (sets.containsKey(session)) return
        val fx = Fx(
            mk { Equalizer(0, session) },
            mk { BassBoost(0, session) },
            mk { Virtualizer(0, session) },
            mk { PresetReverb(0, session) }
        )
        sets[session] = fx
        apply(fx)
    }

    @Synchronized fun detach(session: Int) { sets.remove(session)?.release() }
    @Synchronized fun releaseAll() { sets.values.forEach { it.release() }; sets.clear() }
    @Synchronized fun update() { sets.values.forEach { apply(it) } }

    private fun apply(fx: Fx) {
        val m = modes[mode] ?: modes["off"]!!
        val k = amount / 50f
        val active = enabled && mode != "off"
        try {
            fx.eq?.let { e ->
                val r = e.bandLevelRange
                for (i in 0 until e.numberOfBands.toInt()) {
                    val hz = e.getCenterFreq(i.toShort()) / 1000
                    val db = if (hz < 250) m.low else if (hz < 4000) m.mid else m.high
                    val lvl = (db * 100 * k).toInt().coerceIn(r[0].toInt(), r[1].toInt())
                    e.setBandLevel(i.toShort(), lvl.toShort())
                }
                e.setEnabled(active)
            }
            fx.bass?.let {
                if (it.strengthSupported) it.setStrength((m.bass * k).toInt().coerceIn(0, 1000).toShort())
                it.setEnabled(active && m.bass > 0)
            }
            fx.virt?.let {
                if (it.strengthSupported) it.setStrength((m.virt * k).toInt().coerceIn(0, 1000).toShort())
                it.setEnabled(active && m.virt > 0)
            }
            fx.rev?.let { r ->
                m.reverb?.let { r.setPreset(it) }
                r.setEnabled(active && m.reverb != null && amount >= 10)
            }
        } catch (_: Throwable) {}
    }
}
