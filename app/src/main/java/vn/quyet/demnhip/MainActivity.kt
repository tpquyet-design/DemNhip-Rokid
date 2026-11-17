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
 * Điều khiển kiểu "menu máy ảnh" (phương án B):
 *   - Chạm 1 lần   : chuyển ô đang chọn  BPM -> Nhịp -> Chạy/Dừng -> BPM ...
 *   - Vuốt tới/lui : chỉnh giá trị ô đang chọn
 *        BPM   : +1 / -1 (vuốt liên tiếp nhanh từ cú thứ 3 thì +5 / -5)
 *        Nhịp  : 2/4 <-> 3/4 <-> 4/4 <-> 6/8
 *        Chạy  : vuốt bất kỳ = chạy / dừng
 *   - Chạm 2 lần   : để nguyên cho hệ thống (thoát app)
 */
class MainActivity : Activity(), MetronomeEngine.Listener {

    private lateinit var view: MetronomeView
    private lateinit var engine: MetronomeEngine
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("demnhip", Context.MODE_PRIVATE) }

    private var lastSwipeAt = 0L
    private var lastSwipeDir = 0
    private var fastSwipes = 0

    private val beatHalf = Runnable { view.beatPhase = 1; view.invalidate() }
    private val beatOff = Runnable { view.beatPhase = 0; view.invalidate() }
    private val hideHint = Runnable { view.showHint = false; view.invalidate() }

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

        // Phòng khi kính đưa thao tác về dạng chạm màn hình (hoặc khi thử qua scrcpy)
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { onTap(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y
                val forward = if (abs(dx) > abs(dy)) dx > 0 else dy < 0
                onSwipe(if (forward) 1 else -1)
                return true
            }
        })
        view.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev); true }

        refresh(showHint = true)
    }

    // ---------- Phím hệ thống sinh ra từ bàn cảm ứng ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        when (keyCode) {
            // Vuốt tới / lui: tuỳ firmware là trái/phải hay lên/xuống, nhận cả hai
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> onSwipe(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onSwipe(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> onTap()
            else -> return super.onKeyDown(keyCode, event)   // Back: để hệ thống thoát app
        }
        return true
    }

    // ---------- Hành động ----------
    private fun onTap() {
        view.focusedField = (view.focusedField + 1) % 3
        refresh(showHint = true)
    }

    private fun onSwipe(dir: Int) {
        when (view.focusedField) {
            MetronomeView.FIELD_BPM -> {
                val now = System.currentTimeMillis()
                fastSwipes = if (dir == lastSwipeDir && now - lastSwipeAt < 350) fastSwipes + 1 else 0
                lastSwipeAt = now; lastSwipeDir = dir
                engine.bpm = engine.bpm + dir * (if (fastSwipes >= 2) 5 else 1)
            }
            MetronomeView.FIELD_SIG -> {
                engine.signatureIndex = engine.signatureIndex + dir
                view.currentBeat = -1
            }
            else -> {
                if (engine.isRunning) engine.stop() else engine.start()
                view.currentBeat = -1
            }
        }
        refresh()
    }

    private fun refresh(showHint: Boolean = false) {
        view.bpm = engine.bpm
        view.signature = engine.signature
        view.running = engine.isRunning
        view.hint = getString(
            when (view.focusedField) {
                MetronomeView.FIELD_BPM -> R.string.hint_bpm
                MetronomeView.FIELD_SIG -> R.string.hint_sig
                else -> R.string.hint_play
            }
        )
        if (showHint) {
            view.showHint = true
            ui.removeCallbacks(hideHint)
            ui.postDelayed(hideHint, 5000)
        }
        view.invalidate()
    }

    // ---------- Từ luồng âm thanh: vẽ 3 lần mỗi phách (nở hết -> nở nửa -> nghỉ) ----------
    override fun onBeat(beat: Int, accent: Boolean) {
        ui.post {
            view.currentBeat = beat
            view.beatAccent = accent
            view.beatPhase = 2
            view.invalidate()
            ui.removeCallbacks(beatHalf); ui.removeCallbacks(beatOff)
            ui.postDelayed(beatHalf, 80)
            ui.postDelayed(beatOff, 120)
        }
    }

    override fun onPause() {
        super.onPause()
        engine.stop()
        ui.removeCallbacks(beatHalf); ui.removeCallbacks(beatOff)
        view.beatPhase = 0
        prefs.edit().putInt("bpm", engine.bpm).putInt("sig", engine.signatureIndex).apply()
        refresh()
    }
}
