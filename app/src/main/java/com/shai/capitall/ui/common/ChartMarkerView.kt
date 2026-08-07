package com.shai.capitall.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.shai.capitall.R

/**
 * תווית צפה בסגנון TradingView: בלחיצה/גרירה על הגרף מופיע קו אנכי מהנקודה,
 * ובראש הגרף תיבה עם הערך והתאריך של אותה נקודה.
 */
@SuppressLint("ViewConstructor")
class ChartMarkerView(
    context: Context,
    private var dateLabels: List<String>,
    private var formatValue: (Float) -> String
) : MarkerView(context, R.layout.marker_chart) {

    private val tvValue: TextView = findViewById(R.id.tvMarkerValue)
    private val tvDate: TextView = findViewById(R.id.tvMarkerDate)

    fun update(dateLabels: List<String>, formatValue: (Float) -> String) {
        this.dateLabels = dateLabels
        this.formatValue = formatValue
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            tvValue.text = formatValue(e.y)
            tvDate.text = dateLabels.getOrNull(e.x.toInt()).orEmpty()
        }
        super.refreshContent(e, highlight)
    }

    // ממקם את התיבה בראש הגרף וממורכזת אופקית על הנקודה (עם הצמדה לגבולות)
    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        val markerWidth = width.toFloat()
        var offsetX = -markerWidth / 2f
        chartView?.let { chart ->
            if (posX + offsetX < 0f) {
                offsetX = -posX
            } else if (posX + offsetX + markerWidth > chart.width) {
                offsetX = chart.width - posX - markerWidth
            }
        }
        val offsetY = -posY + 12f // הצמדה לראש הגרף
        return MPPointF(offsetX, offsetY)
    }
}
