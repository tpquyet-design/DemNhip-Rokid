package vn.quyet.demnhip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Giao diện tiết kiệm pin cho màn microLED đơn sắc của Rokid RV101 (480x640):
 *  - nền đen, mọi thứ vẽ bằng NÉT MẢNH, không có mảng tô đặc
 *  - không nháy cả khối: khi có phách chỉ nét vòng tròn dày lên một chút
 *  - chỉ vẽ lại khi có sự kiện (phách tới / tắt, đổi giá trị), không vẽ liên tục
 *
 * Bố cục 3 ô, chạm để chuyển ô (viền mảnh quanh ô đang chọn), vuốt để chỉnh:
 *   [ BPM ]  [ Nhịp ]  [ Chạy/Dừng ]
 */
class MetronomeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val FIELD_BPM = 0
        const val FIELD_SIG = 1
        const val FIELD_PLAY = 2
    }

    var bpm = 100
    var signature: MetronomeEngine.TimeSignature = MetronomeEngine.SIGNATURES[2]
    var running = false
    var focusedField = FIELD_PLAY
    var currentBeat = -1
    var beatLit = false          // true trong ~80 ms sau mỗi phách
    var beatAccent = false
    var hint = ""
    var showHint = false

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.MONOSPACE      // chữ thường, nét mảnh
    }
    private val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666"); typeface = Typeface.MONOSPACE
    }

    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(20f)

        // ---------- 3 ô giá trị, xếp dọc ----------
        val rowH = dp(54f)
        val top0 = dp(28f)
        val labels = arrayOf("BPM", "Nhịp", "")
        val values = arrayOf(
            bpm.toString(),
            signature.label,
            if (running) "■ Dừng" else "▶ Chạy"
        )
        for (i in 0..2) {
            val top = top0 + i * (rowH + dp(10f))
            val base = top + rowH * 0.68f
            if (labels[i].isNotEmpty()) {
                dimText.textSize = sp(16f); dimText.textAlign = Paint.Align.LEFT
                canvas.drawText(labels[i], pad + dp(10f), base, dimText)
            }
            text.textSize = sp(if (i == FIELD_BPM) 40f else 28f)
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(values[i], w - pad - dp(10f), base, text)
            if (i == focusedField) {
                // viền mảnh quanh ô đang chọn
                stroke.strokeWidth = 2f
                canvas.drawRect(pad, top, w - pad, top + rowH, stroke)
            }
        }

        // ---------- Vòng tròn nhịp: chỉ nét, không tô ----------
        val cy = h * 0.70f
        val r = dp(if (beatLit && beatAccent) 44f else if (beatLit) 40f else 36f)
        stroke.strokeWidth = if (beatLit) 5f else 2f
        canvas.drawCircle(w / 2, cy, r, stroke)

        // Gạch phách: gạch ngắn, phách đang chạy dài hơn; phách mạnh có 2 gạch chồng
        val n = signature.beats
        val gap = dp(26f)
        val y = cy + dp(58f)
        val startX = w / 2 - gap * (n - 1) / 2f
        stroke.strokeWidth = 2f
        for (i in 0 until n) {
            val x = startX + i * gap
            val half = dp(if (running && i == currentBeat) 9f else 4f)
            canvas.drawLine(x - half, y, x + half, y, stroke)
            if (i in signature.accents) canvas.drawLine(x - half, y + dp(4f), x + half, y + dp(4f), stroke)
        }

        // ---------- Gợi ý thao tác (chỉ hiện vài giây) ----------
        if (showHint && hint.isNotEmpty()) {
            dimText.textSize = sp(12f); dimText.textAlign = Paint.Align.CENTER
            drawWrapped(canvas, hint, w / 2, h - dp(26f), w - 2 * pad, dimText)
        }
    }

    private fun drawWrapped(c: Canvas, s: String, cx: Float, yBottom: Float, maxW: Float, p: Paint) {
        val lines = ArrayList<String>()
        var line = ""
        for (wd in s.split(" ")) {
            val t = if (line.isEmpty()) wd else "$line $wd"
            if (p.measureText(t) > maxW && line.isNotEmpty()) { lines.add(line); line = wd } else line = t
        }
        if (line.isNotEmpty()) lines.add(line)
        var y = yBottom - (lines.size - 1) * p.textSize * 1.3f
        for (l in lines) { c.drawText(l, cx, y, p); y += p.textSize * 1.3f }
    }
}
