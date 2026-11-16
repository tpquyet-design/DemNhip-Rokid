package vn.quyet.demnhip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Màn hình đếm nhịp vẽ tay, tối ưu cho kính Rokid RV101:
 * màn 480x640 đơn sắc xanh lá -> nền đen, chữ to, ít chi tiết.
 */
class MetronomeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var bpm = 100
    var signature: MetronomeEngine.TimeSignature = MetronomeEngine.SIGNATURES[2]
    var running = false
    var currentBeat = -1          // -1: chưa chạy
    var flashUntil = 0L           // thời điểm hết sáng của vòng nhịp
    var flashAccent = false
    var hint = ""

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF00") }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#777777") }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA"); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val mono = Typeface.MONOSPACE

    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val now = System.currentTimeMillis()
        val lit = running && now < flashUntil

        // --- Tiêu đề + loại nhịp ---
        white.typeface = Typeface.create(mono, Typeface.BOLD)
        white.textSize = sp(20f); white.textAlign = Paint.Align.LEFT
        canvas.drawText("ĐẾM NHỊP", dp(24f), dp(44f), white)
        green.typeface = Typeface.create(mono, Typeface.BOLD)
        green.textSize = sp(26f); green.textAlign = Paint.Align.RIGHT
        canvas.drawText("Nhịp " + signature.label, w - dp(24f), dp(46f), green)

        // --- Vòng tròn nhịp đập ---
        val r = dp(70f)
        val cy = h * 0.40f
        canvas.drawCircle(cx, cy, r, ring)
        if (lit) {
            val rr = if (flashAccent) r else r * 0.8f
            canvas.drawCircle(cx, cy, rr, if (flashAccent) green else white)
        }

        // --- Số BPM thật to ---
        // Khi vòng tròn sáng thì số chuyển sang màu đen để nổi trên nền sáng
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        numPaint.color = if (lit) Color.BLACK else Color.WHITE
        numPaint.typeface = Typeface.create(mono, Typeface.BOLD)
        numPaint.textSize = sp(if (lit) 48f else 56f)
        numPaint.textAlign = Paint.Align.CENTER
        val baseline = cy + numPaint.textSize * 0.36f
        canvas.drawText(bpm.toString(), cx, baseline, numPaint)

        dim.typeface = mono; dim.textSize = sp(18f); dim.textAlign = Paint.Align.CENTER
        canvas.drawText("BPM", cx, cy + r + dp(28f), dim)

        // --- Các chấm phách trong ô nhịp ---
        val n = signature.beats
        val gap = dp(34f)
        val startX = cx - gap * (n - 1) / 2f
        val dotY = h * 0.70f
        for (i in 0 until n) {
            val x = startX + i * gap
            val accent = i in signature.accents
            val isCur = running && i == currentBeat
            val rad = if (accent) dp(10f) else dp(7f)
            if (isCur) canvas.drawCircle(x, dotY, rad + dp(3f), if (accent) green else white)
            else canvas.drawCircle(x, dotY, rad, ring)
        }

        // --- Trạng thái + gợi ý thao tác ---
        white.typeface = Typeface.create(mono, Typeface.BOLD)
        white.textSize = sp(22f); white.textAlign = Paint.Align.CENTER
        canvas.drawText(if (running) "▶ ĐANG CHẠY" else "■ DỪNG", cx, h * 0.80f, white)

        dim.textSize = sp(13f)
        drawWrapped(canvas, hint, cx, h * 0.88f, w - dp(32f), dim)

        if (running) postInvalidateOnAnimation()
    }

    private fun drawWrapped(c: Canvas, text: String, cx: Float, y: Float, maxW: Float, p: Paint) {
        val words = text.split(" ")
        var line = ""
        var yy = y
        for (wd in words) {
            val trial = if (line.isEmpty()) wd else "$line $wd"
            if (p.measureText(trial) > maxW && line.isNotEmpty()) {
                c.drawText(line, cx, yy, p); yy += p.textSize * 1.3f; line = wd
            } else line = trial
        }
        if (line.isNotEmpty()) c.drawText(line, cx, yy, p)
    }
}
