package vn.quyet.demnhip

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Bộ máy đếm nhịp: phát tiếng "tích" bằng AudioTrack ở chế độ stream.
 *
 * Cách làm: mỗi phách là một khối PCM = tiếng tích + khoảng lặng, ghi liên tục
 * vào AudioTrack. Nhờ vậy nhịp chính xác theo mẫu âm thanh (sample-accurate),
 * không phụ thuộc vào Thread.sleep hay Handler vốn hay trôi trên Android.
 */
class MetronomeEngine(private val listener: Listener) {

    interface Listener {
        /** Gọi ngay trước khi phách được đưa vào loa. beat: 0-based trong ô nhịp. */
        fun onBeat(beat: Int, accent: Boolean)
    }

    data class TimeSignature(val label: String, val beats: Int, val accents: Set<Int>)

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 250
        private const val SAMPLE_RATE = 44100
        private const val CHUNK_FRAMES = 512

        val SIGNATURES = listOf(
            TimeSignature("2/4", 2, setOf(0)),
            TimeSignature("3/4", 3, setOf(0)),
            TimeSignature("4/4", 4, setOf(0)),
            TimeSignature("6/8", 6, setOf(0, 3)),
        )
    }

    @Volatile var bpm: Int = 100
        set(value) { field = value.coerceIn(MIN_BPM, MAX_BPM) }

    @Volatile var signatureIndex: Int = 2
        set(value) { field = ((value % SIGNATURES.size) + SIGNATURES.size) % SIGNATURES.size }

    val signature: TimeSignature get() = SIGNATURES[signatureIndex]

    @Volatile var isRunning = false
        private set

    private var thread: Thread? = null

    // Tiếng tích: phách mạnh cao hơn và dài hơn một chút
    private val accentClick = makeClick(freqHz = 1600.0, ms = 55, gain = 0.95)
    private val normalClick = makeClick(freqHz = 1000.0, ms = 40, gain = 0.75)

    private fun makeClick(freqHz: Double, ms: Int, gain: Double): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 90.0)            // tắt dần nhanh cho gọn tiếng
            val attack = if (i < 40) i / 40.0 else 1.0   // tránh "bụp" đầu tiếng
            val v = sin(2 * PI * freqHz * t) * env * attack * gain
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        thread = Thread(::runLoop, "MetronomeAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning = false
        thread?.join(500)
        thread = null
    }

    private fun runLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = maxOf(minBuf, CHUNK_FRAMES * 2 * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        try {
            track.play()
            var beat = 0
            val chunk = ShortArray(CHUNK_FRAMES)

            while (isRunning) {
                val sig = signature
                if (beat >= sig.beats) beat = 0
                val accent = beat in sig.accents
                val click = if (accent) accentClick else normalClick
                val framesPerBeat = SAMPLE_RATE * 60 / bpm

                listener.onBeat(beat, accent)

                // Ghi phách theo từng khối nhỏ để có thể dừng/đổi BPM gần như tức thì
                var written = 0
                while (written < framesPerBeat && isRunning) {
                    val n = minOf(CHUNK_FRAMES, framesPerBeat - written)
                    for (i in 0 until n) {
                        val pos = written + i
                        chunk[i] = if (pos < click.size) click[pos] else 0
                    }
                    track.write(chunk, 0, n)   // chặn cho tới khi có chỗ trống -> giữ nhịp
                    written += n
                }
                beat++
            }
        } finally {
            try { track.pause(); track.flush() } catch (_: Exception) {}
            track.release()
        }
    }
}
