package com.shai.capitall.ui.stockchart
import com.shai.capitall.util.CurrencyConverter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shai.capitall.R
import com.shai.capitall.data.repository.ChartRange
import com.shai.capitall.data.repository.PricePoint
import com.shai.capitall.databinding.BottomsheetStockChartBinding
import com.shai.capitall.ui.common.ChartMarkerView
import com.shai.capitall.util.hapticTap
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockChartBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_SYMBOL = "arg_symbol"
        private const val ARG_NAME = "arg_name"

        fun newInstance(symbol: String, name: String): StockChartBottomSheet =
            StockChartBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                    putString(ARG_NAME, name)
                }
            }
    }

    private var _binding: BottomsheetStockChartBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: StockChartViewModel
    private lateinit var rangeButtons: Map<ChartRange, TextView>
    private val currency = CurrencyConverter.usdFormatter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetStockChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val symbol = arguments?.getString(ARG_SYMBOL).orEmpty()
        val name = arguments?.getString(ARG_NAME).orEmpty()
        if (symbol.isBlank()) {
            dismiss()
            return
        }

        binding.tvSymbol.text = symbol
        binding.tvCompanyName.text = name

        viewModel = ViewModelProvider(
            this,
            StockChartViewModelFactory(symbol)
        )[StockChartViewModel::class.java]

        rangeButtons = mapOf(
            ChartRange.DAY to binding.btnRange1D,
            ChartRange.WEEK to binding.btnRange1W,
            ChartRange.MONTH to binding.btnRange1M,
            ChartRange.SIX_MONTHS to binding.btnRange6M,
            ChartRange.YEAR to binding.btnRange1Y,
            ChartRange.FIVE_YEARS to binding.btnRange5Y,
            ChartRange.MAX to binding.btnRangeMax
        )
        rangeButtons.forEach { (range, button) ->
            button.setOnClickListener {
                it.hapticTap()
                viewModel.loadRange(range)
            }
        }

        setupChartAppearance()

        viewModel.state.observe(viewLifecycleOwner) { state -> render(state) }
    }

    override fun onStart() {
        super.onStart()
        // פתיחה במצב מורחב כדי שהגרף ייראה במלואו מיד
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun setupChartAppearance() {
        val gray = resources.getColor(R.color.gray_light, null)
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.textColor = gray
            axisLeft.setDrawAxisLine(false)
            // קווי רשת אופקיים מקווקווים ועדינים — כמו בגרף השווי הנקי בדשבורד
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = resources.getColor(R.color.hairline, null)
            axisLeft.enableGridDashedLine(10f, 8f, 0f)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = gray
            xAxis.setDrawAxisLine(false)
            xAxis.setDrawGridLines(false)
            // שתי אצבעות שמורות למצב ההשוואה — מבטלים זום/צביטה כדי שלא יתנגשו במחווה
            isDoubleTapToZoomEnabled = false
            setPinchZoom(false)
            setScaleEnabled(false)
            setNoDataText("")
        }
    }

    private fun render(state: StockChartState) {
        highlightSelectedRange()
        binding.progressBar.visibility = if (state is StockChartState.Loading) View.VISIBLE else View.GONE
        binding.tvChartError.visibility = if (state is StockChartState.Error) View.VISIBLE else View.GONE
        binding.lineChart.visibility = if (state is StockChartState.Data) View.VISIBLE else View.INVISIBLE
        binding.tvSelectedPoint.text = ""

        if (state !is StockChartState.Data) return

        binding.tvPrice.text = currency.format(state.lastPrice)

        val sign = if (state.changeAmount >= 0) "+" else ""
        val changeText = "$sign${currency.format(state.changeAmount)} ($sign${String.format(Locale.US, "%.2f", state.changePercent)}%)"
        binding.tvChange.text = changeText
        val trendColor = if (state.changeAmount >= 0) {
            resources.getColor(R.color.green_positive, null)
        } else {
            resources.getColor(R.color.red_negative, null)
        }
        binding.tvChange.setTextColor(trendColor)

        renderChart(state.points, state.range, trendColor)
    }

    private fun highlightSelectedRange() {
        // הצבעים והרקע מגיעים מסלקטורים לפי state_selected (גלולה כחולה-רכה לנבחר)
        rangeButtons.forEach { (range, button) ->
            button.isSelected = range == viewModel.currentRange
        }
    }

    private fun renderChart(points: List<PricePoint>, range: ChartRange, trendColor: Int) {
        val dateFormat = xAxisFormatFor(range)
        val labels = points.map { dateFormat.format(Date(it.timestamp)) }
        val entries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.price.toFloat()) }
        val gold = resources.getColor(R.color.gold_accent, null)

        // תווית צפה בסגנון TradingView: ערך + תאריך מלא לכל נקודה
        val markerDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val markerLabels = points.map { markerDateFormat.format(Date(it.timestamp)) }
        binding.lineChart.marker = ChartMarkerView(requireContext(), markerLabels) { currency.format(it) }

        val dataSet = LineDataSet(entries, "").apply {
            color = trendColor
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = trendColor
            fillAlpha = 40
            highLightColor = gold
            setDrawHorizontalHighlightIndicator(false)
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = 4
            xAxis.granularity = 1f
            // מצב השוואה בשתי אצבעות: איפוס בכל החלפת טווח + אותם פורמטים של התווית הצפה (דולר)
            clearCompare()
            compareValueFormatter = { v -> currency.format(v.toDouble()) }
            compareDateFormatter = { i -> markerLabels.getOrNull(i) ?: "" }
            invalidate()
        }
    }

    private fun xAxisFormatFor(range: ChartRange): SimpleDateFormat {
        val pattern = when (range) {
            ChartRange.DAY, ChartRange.WEEK -> "HH:mm"
            ChartRange.MONTH, ChartRange.SIX_MONTHS, ChartRange.YEAR -> "dd/MM"
            ChartRange.FIVE_YEARS, ChartRange.MAX -> "MM/yy"
        }
        return SimpleDateFormat(pattern, Locale.getDefault())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
