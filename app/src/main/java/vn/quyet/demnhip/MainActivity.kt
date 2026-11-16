package vn.quyet.demnhip

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import kotlin.math.abs

/**
 * Điều khiển bằng bàn cảm ứng trên gọng kính Rokid (gửi về dạng phím D-pad):
 *   - Chạm 1 lần   : chạy / dừng
 *   - Chạm 2 lần   : đổi loại nhịp (2/4 → 3/4 → 4/4 → 6/8)
 *   - Vuốt lên/xuống: BPM ±5
 *   - Vuốt trái/phải: BPM ±1
 *   - Giữ lâu (Back): thoát
 * Đồng thời nhận vuốt/chạm màn hình thật khi chiếu kính qua scrcpy để thử.
 */
class MainActivity : Activity(), MetronomeEngine.Listener {

    private lateinit var view: MetronomeView
    private lateinit var engine: MetronomeEngine
    private val ui = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("demnhip", Context.MODE_PRIVATE) }

    // Phân biệt chạm 1 lần / 2 lần từ phím Enter/DPAD_CENTER
    private val doubleTapMs = 350L
    private var pendingSingleTap: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        engine = MetronomeEngine(this)
        engine.bpm = prefs.getInt("bpm", 100)
        engine.signatureIndex = prefs.getInt("sig", 2)

        view = MetronomeView(this)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        setContentView(view)
        view.requestFocus()

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { toggle(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { cycleSignature(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y
                if (abs(dx) > abs(dy)) changeBpm(if (dx > 0) 1 else -1)
                else changeBpm(if (dy < 0) 5 else -5)
                return true
            }
        })
        view.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev); true }

        refresh()
    }

    // ---------- Phím từ bàn cảm ứng trên gọng kính ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> changeBpm(5)
            KeyEvent.KEYCODE_DPAD_DOWN -> changeBpm(-5)
            KeyEvent.KEYCODE_DPAD_RIGHT -> changeBpm(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> changeBpm(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> onTapKey()
            KeyEvent.KEYCODE_VOLUME_UP -> changeBpm(1)      // phòng khi kính gửi phím âm lượng
            KeyEvent.KEYCODE_VOLUME_DOWN -> changeBpm(-1)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun onTapKey() {
        val pending = pendingSingleTap
        if (pending != null) {           // chạm lần 2 trong khoảng cho phép -> double tap
            ui.removeCallbacks(pending)
            pendingSingleTap = null
            cycleSignature()
        } else {
            val r = Runnable { pendingSingleTap = null; toggle() }
            pendingSingleTap = r
            ui.postDelayed(r, doubleTapMs)
        }
    }

    // ---------- Hành động ----------
    private fun toggle() {
        if (engine.isRunning) engine.stop() else engine.start()
        view.currentBeat = -1
        refresh()
    }

    private fun changeBpm(delta: Int) {
        engine.bpm = engine.bpm + delta
        refresh()
    }

    private fun cycleSignature() {
        engine.signatureIndex = engine.signatureIndex + 1
        view.currentBeat = -1
        refresh()
    }

    private fun refresh() {
        view.bpm = engine.bpm
        view.signature = engine.signature
        view.running = engine.isRunning
        view.hint = getString(if (engine.isRunning) R.string.hint_running else R.string.hint_stopped)
        view.invalidate()
    }

    // ---------- Từ luồng âm thanh ----------
    override fun onBeat(beat: Int, accent: Boolean) {
        ui.post {
            view.currentBeat = beat
            view.flashAccent = accent
            view.flashUntil = System.currentTimeMillis() + 90
            view.invalidate()
        }
    }

    override fun onPause() {
        super.onPause()
        engine.stop()
        prefs.edit().putInt("bpm", engine.bpm).putInt("sig", engine.signatureIndex).apply()
        refresh()
    }
}
