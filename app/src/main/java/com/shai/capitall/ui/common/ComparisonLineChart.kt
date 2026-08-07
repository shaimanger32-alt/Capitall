package com.shai.capitall.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.DataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.utils.MPPointD
import com.github.mikephil.charting.utils.Utils
import com.shai.capitall.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * גרף קו עם מצב השוואה בסגנון TradingView: הנחת שתי אצבעות מציגה שני קווי סמן
 * הנצמדים לנקודות הנתונים, עם תיבות ערך/תאריך מעל כל קו ותיבת הפרש (₪ + %) ביניהם.
 * ההשוואה חיה רק כל עוד שתי האצבעות על המסך — הרמת אצבע מחזירה את הגרף לתצוגה רגילה.
 * האזור שמחוץ לטווח הנבחר מעומעם למיקוד. ההשוואה נעשית תמיד מול הסדרה הראשית (dataset 0).
 */
class ComparisonLineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LineChart(context, attrs, defStyle) {

    /** ממיר ערך Y לטקסט מטבע — מוזרק מהמסך כדי לשמור על פורמט אחיד בכל האפליקציה */
    var compareValueFormatter: (Float) -> String = { it.toString() }

    /** ממיר אינדקס X לתווית תאריך מלאה */
    var compareDateFormatter: (Int) -> String = { "" }

    private var compareActive = false
    private var touchX1 = 0f
    private var touchX2 = 0f
    private var pointerId1 = -1
    private var pointerId2 = -1

    // אחרי סיום השוואה באמצע מחווה — בולעים את שאר האירועים עד הרמת האצבע האחרונה
    private var swallowUntilUp = false

    private val padH = Utils.convertDpToPixel(8f)
    private val padV = Utils.convertDpToPixel(4.5f)
    private val boxGap = Utils.convertDpToPixel(4f)
    private val lineGap = Utils.convertDpToPixel(2f)
    private val cornerRadius = Utils.convertDpToPixel(8f)
    private val dotRadius = Utils.convertDpToPixel(4f)

    private val colorText = context.getColor(R.color.on_surface)
    private val colorMuted = context.getColor(R.color.on_surface_muted)
    private val colorPositive = context.getColor(R.color.green_positive)
    private val colorNegative = context.getColor(R.color.red_negative)
    private val colorPositiveBg = context.getColor(R.color.green_soft_bg)
    private val colorNegativeBg = context.getColor(R.color.red_soft_bg)
    private val colorCard = context.getColor(R.color.surface_card)

    private val scrimPaint = Paint().apply {
        color = (colorCard and 0x00FFFFFF) or (0xA8 shl 24)
        style = Paint.Style.FILL
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (colorMuted and 0x00FFFFFF) or (0xB4 shl 24)
        strokeWidth = Utils.convertDpToPixel(1.2f)
        style = Paint.Style.STROKE
    }
    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Utils.convertDpToPixel(2f)
        color = colorCard
    }
    private val boxBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Utils.convertDpToPixel(1f)
        color = context.getColor(R.color.hairline)
    }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Utils.convertDpToPixel(10.5f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Utils.convertDpToPixel(9f)
        textAlign = Paint.Align.CENTER
    }

    /** מבטל את מצב ההשוואה — נקרא גם מהמסך בכל רינדור נתונים מחדש (האינדקסים משתנים) */
    fun clearCompare() {
        if (!compareActive) return
        compareActive = false
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (data == null || data.entryCount == 0) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                // אצבע שנייה נוחתת → כניסה למצב השוואה; מבטלים לחלוטין את מחוות הגרף הרגילות
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.onTouchEvent(cancel)
                cancel.recycle()
                highlightValue(null)

                compareActive = true
                pointerId1 = event.getPointerId(0)
                pointerId2 = event.getPointerId(1)
                touchX1 = clampX(event.getX(0))
                touchX2 = clampX(event.getX(1))
                parent?.requestDisallowInterceptTouchEvent(true)
                // רטט עדין בכניסה למצב השוואה — פידבק שהמחווה נקלטה
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> if (compareActive) {
                val i1 = event.findPointerIndex(pointerId1)
                val i2 = event.findPointerIndex(pointerId2)
                if (i1 >= 0) touchX1 = clampX(event.getX(i1))
                if (i2 >= 0) touchX2 = clampX(event.getX(i2))
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> if (compareActive) {
                // הרמת אצבע מסיימת את ההשוואה מיד — הגרף חוזר לתצוגה רגילה
                clearCompare()
                swallowUntilUp = true
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (compareActive || swallowUntilUp) {
                clearCompare()
                swallowUntilUp = false
                return true
            }
        }
        if (swallowUntilUp) return true
        return if (compareActive) true else super.onTouchEvent(event)
    }

    private fun clampX(x: Float): Float {
        val content = viewPortHandler.contentRect
        return x.coerceIn(content.left, content.right)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (compareActive) drawCompare(canvas)
    }

    private fun drawCompare(canvas: Canvas) {
        val set = data?.getDataSetByIndex(0) ?: return
        if (set.entryCount == 0) return
        val trans = getTransformer(YAxis.AxisDependency.LEFT)
        val content = viewPortHandler.contentRect

        fun entryAt(px: Float): Entry? {
            val p = trans.getValuesByTouchPoint(px, 0f)
            val e = set.getEntryForXValue(p.x.toFloat(), Float.NaN, DataSet.Rounding.CLOSEST)
            MPPointD.recycleInstance(p)
            return e
        }

        val e1 = entryAt(min(touchX1, touchX2)) ?: return
        val e2 = entryAt(max(touchX1, touchX2)) ?: return

        fun pixelFor(e: Entry): Pair<Float, Float> {
            val p = trans.getPixelForValues(e.x, e.y)
            val result = p.x.toFloat() to p.y.toFloat()
            MPPointD.recycleInstance(p)
            return result
        }

        // הקווים נצמדים לנקודות נתונים אמיתיות — לא למיקום האצבע הגולמי
        val (lx1, ly1) = pixelFor(e1)
        val (lx2, ly2) = pixelFor(e2)

        // עמעום האזור שמחוץ לטווח הנבחר — ממקד את העין בקטע המושווה
        canvas.drawRect(content.left, content.top, lx1, content.bottom, scrimPaint)
        canvas.drawRect(lx2, content.top, content.right, content.bottom, scrimPaint)

        canvas.drawLine(lx1, content.top, lx1, content.bottom, cursorPaint)
        canvas.drawLine(lx2, content.top, lx2, content.bottom, cursorPaint)

        dotFillPaint.color = set.color
        canvas.drawCircle(lx1, ly1, dotRadius, dotFillPaint)
        canvas.drawCircle(lx1, ly1, dotRadius, dotStrokePaint)
        canvas.drawCircle(lx2, ly2, dotRadius, dotFillPaint)
        canvas.drawCircle(lx2, ly2, dotRadius, dotStrokePaint)

        // ההפרש כרונולוגי: הנקודה הימנית (המאוחרת) פחות השמאלית (המוקדמת)
        val delta = e2.y - e1.y
        val positive = delta >= 0f
        val deltaText = (if (positive) "+" else "") + compareValueFormatter(delta)
        val pctText = if (e1.y != 0f)
            String.format(Locale.US, "%+.2f%%", delta / abs(e1.y) * 100) else ""

        val t1a = compareValueFormatter(e1.y)
        val t1b = compareDateFormatter(e1.x.toInt())
        val t2a = compareValueFormatter(e2.y)
        val t2b = compareDateFormatter(e2.x.toInt())

        val w1 = boxWidth(t1a, t1b)
        val wD = boxWidth(deltaText, pctText)
        val w2 = boxWidth(t2a, t2b)
        val hD = boxHeight(pctText.isNotEmpty())
        val hV = boxHeight(true)

        // שתי שורות: ההפרש למעלה, תיבות הערך מתחתיו. ההפרדה מונעת התנגשות בין
        // שלוש התיבות — ולכן כל אחת עוקבת ברציפות אחרי האצבע שלה בלי קפיצות.
        val rowDelta = content.top + boxGap
        val rowValues = rowDelta + hD + boxGap

        // תיבת ההפרש עוקבת ברציפות אחרי אמצע המרחק בין שתי האצבעות
        val dLeft = clampLeft(wD, (lx1 + lx2) / 2f, content)
        var left1 = clampLeft(w1, lx1, content)
        var left2 = clampLeft(w2, lx2, content)

        // כשהאצבעות מתקרבות, תיבות הערך נפרשות סימטרית סביב האמצע במקום לחפוף
        if (left1 + w1 + boxGap > left2) {
            val mid = (lx1 + lx2) / 2f
            left1 = clampLeft(w1, mid - (w1 + boxGap) / 2f, content)
            left2 = clampLeft(w2, mid + (w2 + boxGap) / 2f, content)
        }

        drawBox(
            canvas, dLeft, rowDelta, wD, hD, deltaText, pctText,
            if (positive) colorPositiveBg else colorNegativeBg,
            if (positive) colorPositive else colorNegative,
            if (positive) colorPositive else colorNegative,
            false
        )
        drawBox(canvas, left1, rowValues, w1, hV, t1a, t1b, colorCard, colorText, colorMuted, true)
        drawBox(canvas, left2, rowValues, w2, hV, t2a, t2b, colorCard, colorText, colorMuted, true)
    }

    /** ממקם תיבה במרכז cx, תחום לגבולות אזור הציור. עמיד לתיבה רחבה מהגרף (coerceIn היה קורס). */
    private fun clampLeft(w: Float, cx: Float, content: RectF): Float {
        val minLeft = content.left + boxGap
        val maxLeft = content.right - w - boxGap
        if (maxLeft <= minLeft) return minLeft
        return (cx - w / 2f).coerceIn(minLeft, maxLeft)
    }

    private fun boxWidth(line1: String, line2: String): Float =
        max(valueTextPaint.measureText(line1), subTextPaint.measureText(line2)) + padH * 2

    private fun boxHeight(hasSecondLine: Boolean): Float {
        val h1 = valueTextPaint.descent() - valueTextPaint.ascent()
        val h2 = if (hasSecondLine) subTextPaint.descent() - subTextPaint.ascent() + lineGap else 0f
        return padV * 2 + h1 + h2
    }

    private fun drawBox(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        line1: String,
        line2: String,
        bgColor: Int,
        line1Color: Int,
        line2Color: Int,
        withStroke: Boolean
    ) {
        val rect = RectF(left, top, left + width, top + height)
        boxBgPaint.color = bgColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxBgPaint)
        if (withStroke) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxStrokePaint)

        valueTextPaint.color = line1Color
        val baseline1 = top + padV - valueTextPaint.ascent()
        canvas.drawText(line1, rect.centerX(), baseline1, valueTextPaint)

        if (line2.isNotEmpty()) {
            subTextPaint.color = line2Color
            val baseline2 = baseline1 + valueTextPaint.descent() + lineGap - subTextPaint.ascent()
            canvas.drawText(line2, rect.centerX(), baseline2, subTextPaint)
        }
    }
}
