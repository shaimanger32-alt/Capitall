package com.shai.capitall.ui.deepdive
import com.shai.capitall.util.CurrencyConverter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.shai.capitall.R
import com.shai.capitall.databinding.ActivityDeepDiveBinding
import com.shai.capitall.ui.addasset.AddAssetActivity
import com.shai.capitall.ui.addtransaction.AddTransactionActivity
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.playRiseAnimation
import java.text.NumberFormat
import java.util.Locale

class DeepDiveActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
    }

    private lateinit var binding: ActivityDeepDiveBinding
    private lateinit var viewModel: DeepDiveViewModel
    private lateinit var category: String
    private val isStockMode: Boolean get() = category == STOCKS_CATEGORY_KEY
    private val monthOptions = listOf(3, 6, 12)
    private var monthOptionIndex = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeepDiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        category = intent.getStringExtra(EXTRA_CATEGORY) ?: return finish()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // כותרת עשירה: שם הקטגוריה מוצג בגוף המסך (במקום כותרת ה-toolbar)
        binding.tvHeaderTitle.text = CategoryCatalog.labelFor(this, category)

        viewModel = ViewModelProvider(this, DeepDiveViewModelFactory(category))[DeepDiveViewModel::class.java]

        setupHeaderActions()
        setupRangeChips()
        setupBudgetToggle()

        binding.btnRetry.setOnClickListener { viewModel.load() }

        viewModel.uiState.observe(this) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.errorState.visibility = if (state.isError) View.VISIBLE else View.GONE
            // מצב "ריק" מוצג רק כשאין טעינה/שגיאה ואין נתונים
            val showEmpty = !state.isLoading && !state.isError && !state.hasData
            binding.emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
            binding.contentGroup.visibility = if (state.hasData) View.VISIBLE else View.GONE

            if (state.hasData) {
                applyModeChrome()
                renderSummaryCard(state)
                renderTrendChart(state)
                // ניתוח מניות אינו כולל התפלגות ימי-שבוע (לא רלוונטי לאחזקות)
                if (!isStockMode) renderWeekdayChart(state)
                renderContributors(state)
            }
        }
    }

    // מתאים את התוויות/הכותרות לפי מצב הניתוח (הוצאות מול מניות)
    private fun applyModeChrome() {
        binding.cardPatterns.visibility = if (isStockMode) View.GONE else View.VISIBLE
        if (isStockMode) {
            binding.tvHeaderSubtitle.setText(R.string.deepdive_subtitle_stocks)
            binding.tvKpiCurrentLabel.setText(R.string.deepdive_current_value)
            binding.tvKpiAvgLabel.setText(R.string.deepdive_average_value)
            binding.tvKpiPeakLabel.setText(R.string.deepdive_peak_value)
            binding.tvTrendTitle.setText(R.string.deepdive_value_trend_title)
            binding.tvContributorsTitle.setText(R.string.deepdive_holdings_title)
        }
    }

    private fun setupHeaderActions() {
        binding.btnInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.deepdive_info_title)
                .setMessage(if (isStockMode) R.string.deepdive_info_message_stocks else R.string.deepdive_info_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.fabAddTransaction.setOnClickListener {
            // במצב מניות ה-"+" מוסיף נכס (מניה) עם קטגוריית המניות מוזנת מראש; אחרת עסקה
            if (isStockMode) {
                startActivity(
                    Intent(this, AddAssetActivity::class.java)
                        .putExtra(AddAssetActivity.EXTRA_CATEGORY_KEY, "stocks_investments")
                )
            } else {
                startActivity(Intent(this, AddTransactionActivity::class.java))
            }
        }
    }

    // צ'יפים מקטעים (3/6/12 חודשים) — בורר טווח זמן גלוי במקום מתג מחזורי
    private fun setupRangeChips() {
        binding.chip12.isChecked = true
        binding.chipGroupRange.setOnCheckedStateChangeListener { _, checkedIds ->
            val months = when (checkedIds.firstOrNull()) {
                R.id.chip3 -> 3
                R.id.chip6 -> 6
                else -> 12
            }
            monthOptionIndex = monthOptions.indexOf(months).coerceAtLeast(0)
            viewModel.uiState.value?.let { renderTrendChart(it) }
        }
    }

    // מתג "קו תקציב" — מציג/מסתיר את קו הממוצע בגרף המגמה
    private fun setupBudgetToggle() {
        binding.switchBudgetLine.setOnCheckedChangeListener { _, _ ->
            viewModel.uiState.value?.let { renderTrendChart(it) }
        }
    }

    private fun renderSummaryCard(state: DeepDiveUiState) {
        val format = CurrencyConverter.ilsFormatter()
        binding.tvKpiCurrent.text = format.format(state.currentMonth)
        binding.tvKpiCurrentSub.text = if (state.isStockMode)
            getString(R.string.deepdive_holdings_count, state.currentMonthTxCount)
        else
            getString(R.string.deepdive_transactions_count, state.currentMonthTxCount)
        binding.tvKpiAvg.text = format.format(state.historicalAverage)
        binding.tvKpiPeak.text = format.format(state.allTimePeak)
        binding.tvKpiPeakSub.text = state.peakMonthLabel
    }

    private fun renderTrendChart(state: DeepDiveUiState) {
        val months = state.monthlyPoints.takeLast(monthOptions[monthOptionIndex])
        if (months.isEmpty()) return
        val format = CurrencyConverter.ilsFormatter()
        binding.tvTrendCount.text = getString(R.string.deepdive_items, months.size)

        val entries = months.mapIndexed { index, point -> Entry(index.toFloat(), point.amount) }
        val dataSet = LineDataSet(entries, getString(R.string.deepdive_actual_spending)).apply {
            color = getColor(R.color.gold_accent)
            setCircleColor(getColor(R.color.gold_accent))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.trendChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.textColor = getColor(R.color.white)
            axisRight.isEnabled = false
            axisLeft.textColor = getColor(R.color.gray_light)
            axisLeft.setDrawGridLines(false)

            axisLeft.removeAllLimitLines()
            // קו התקציב מוצג רק כשהמתג פעיל
            if (binding.switchBudgetLine.isChecked) {
                val limitLine = LimitLine(state.budgetThreshold, getString(R.string.deepdive_budget_line)).apply {
                    lineColor = getColor(R.color.red_negative)
                    lineWidth = 1.5f
                    enableDashedLine(10f, 6f, 0f)
                    textColor = getColor(R.color.red_negative)
                    textSize = 10f
                }
                axisLeft.addLimitLine(limitLine)
            }

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(months.map { it.label })
                position = XAxis.XAxisPosition.BOTTOM
                textColor = getColor(R.color.white)
                setDrawGridLines(false)
                granularity = 1f
            }

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e ?: return
                    val index = e.x.toInt().coerceIn(months.indices)
                    Toast.makeText(
                        this@DeepDiveActivity,
                        "${months[index].label}: ${format.format(e.y)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onNothingSelected() = Unit
            })

            animateX(600)
            invalidate()
        }
    }

    private fun renderWeekdayChart(state: DeepDiveUiState) {
        val dayLabels = listOf(
            getString(R.string.weekday_sun), getString(R.string.weekday_mon),
            getString(R.string.weekday_tue), getString(R.string.weekday_wed),
            getString(R.string.weekday_thu), getString(R.string.weekday_fri),
            getString(R.string.weekday_sat)
        )
        val entries = state.weekdayDistribution.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
        val dataSet = BarDataSet(entries, "").apply {
            color = getColor(R.color.gold_accent)
            setDrawValues(false)
        }

        binding.weekdayChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(dayLabels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = getColor(R.color.gray_light)
                textSize = 8f
                setDrawGridLines(false)
                granularity = 1f
            }

            setTouchEnabled(false)
            animateY(600)
            invalidate()
        }

        binding.tvWeekdayInsight.text =
            getString(R.string.deepdive_weekday_insight, state.weekdayThuFriSatPercent)
    }

    private fun renderContributors(state: DeepDiveUiState) {
        binding.tvContributorsCount.text = getString(R.string.deepdive_items, state.contributors.size)
        binding.recyclerViewContributors.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContributors.adapter = ContributorAdapter(state.contributors)
        binding.recyclerViewContributors.playRiseAnimation()
    }
}
