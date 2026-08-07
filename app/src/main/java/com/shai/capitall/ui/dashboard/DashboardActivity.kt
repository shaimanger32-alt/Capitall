package com.shai.capitall.ui.dashboard
import com.shai.capitall.util.CurrencyConverter

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.CapitallApp
import com.shai.capitall.R
import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.repository.CategoryRepository
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.TransactionRepository
import com.shai.capitall.databinding.ActivityDashboardBinding
import com.shai.capitall.ui.addtransaction.AddTransactionActivity
import com.shai.capitall.ui.selectcategory.SelectCategoryActivity
import com.shai.capitall.ui.common.ChartMarkerView
import com.shai.capitall.ui.auth.LoginActivity
import com.shai.capitall.ui.deepdive.DeepDiveActivity
import com.shai.capitall.ui.portfolio.PortfolioActivity
import com.shai.capitall.ui.screener.ScreenerActivity
import com.shai.capitall.util.CategoryCatalog
import com.shai.capitall.util.hapticTap
import com.shai.capitall.util.playRiseAnimation
import com.shai.capitall.util.setValueAnimated
import com.shai.capitall.util.LanguageManager
import com.shai.capitall.util.ThemeManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import android.graphics.drawable.GradientDrawable
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val STOCKS_CATEGORY_KEY = "stocks_investments"
private const val TOTAL_INCOME_KEY = "__total_income__"
private const val DAY_MS = 24L * 60L * 60L * 1000L
// מפתחות ההעדפות מוגדרים במקום אחד — CapitallApp — ומשותפים עם מסך ההגדרות
private const val PREFS_NAME = CapitallApp.PREFS_NAME
private const val KEY_BALANCE_HIDDEN = CapitallApp.KEY_BALANCE_HIDDEN

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var viewModel: DashboardViewModel
    private var kpiAdapter: KpiAdapter? = null
    private var currentChartSelection = ""
    private var currentChartCategoryKey: String? = null // null = "Net Worth" עצמו נבחר
    // הטווח = חלון זמן אמיתי (יום/שבוע/חודש/שנה) המסנן את הנקודות אחורה מעכשיו, כמו באפליקציות מסחר.
    // windowMillis=null → "הכל" (ללא חיתוך). bucket = גרנולריות תצוגה לתוך החלון (לניקיון בטווחים ארוכים).
    private enum class Bucket { DAY, WEEK, MONTH, YEAR, NONE }
    private data class TimeRangeOption(val labelRes: Int, val windowMillis: Long?, val bucket: Bucket)
    private val timeRangeOptions = listOf(
        TimeRangeOption(R.string.dashboard_chart_range_day, DAY_MS, Bucket.NONE),
        TimeRangeOption(R.string.dashboard_chart_range_week, 7L * DAY_MS, Bucket.NONE),
        TimeRangeOption(R.string.dashboard_chart_range_month, 31L * DAY_MS, Bucket.NONE),
        TimeRangeOption(R.string.dashboard_chart_range_year, 365L * DAY_MS, Bucket.WEEK),
        TimeRangeOption(R.string.dashboard_chart_range_all, null, Bucket.NONE)
    )
    private var currentTimeRangeIndex = timeRangeOptions.lastIndex // ברירת מחדל: הכל (ללא חיתוך)
    private var lastRenderedRawPoints: List<ChartPoint> = emptyList()
    private var lastRenderedIsNetWorth: Boolean = true

    // הסתרת השווי הנקי — נשמרת בין הפעלות. הערך האחרון נשמר כדי לרנדר מחדש בלי לחכות לנתונים.
    private var balanceHidden = false
    private var lastNetWorth: Double = 0.0
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // חייב להיות מאותחל כאן, לפני כל פונקציית setup שמשתמשת בו
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]

        // מיגרציה חד-פעמית של מפתחות קטגוריה ישנים בעסקאות (מסעדות/משלוחים/סופר → מזון, Utilities → דיור)
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            lifecycleScope.launch { com.shai.capitall.util.CategoryMigration.runOnce(this@DashboardActivity, uid) }
        }

        setupToolbarAndDrawer()
        populateDrawerHeader()
        setupTicker()
        setupSearchBar()
        setupKpiCards()
        setupCashFlowChart()
        setupDonutChart()
        setupNetWorthChart()
        setupBottomWidgets()

        setupBalanceToggle()

        viewModel.uiState.observe(this) { state ->
            if (state.isError) showPortfolioError() else dismissPortfolioError()
            val format = CurrencyConverter.ilsFormatter()
            lastNetWorth = state.netWorth
            renderNetWorth()
            binding.tvTotalAssets.setValueAnimated(state.totalAssets) { format.format(it) }
            binding.tvTotalLiabilities.setValueAnimated(state.totalLiabilities) { format.format(it) }
            updateKpiCards(state)
            renderTicker(state)
            renderDonutChart(state)
        }

        // נתונים אמיתיים מהעסקאות שהמשתמש הזין — תזרים, עסקאות אחרונות, תובנות ו-KPI
        viewModel.transactions.observe(this) {
            renderCashFlowChart()
            renderBottomWidgets()
            viewModel.uiState.value?.let {
                updateKpiCards(it)
                renderTicker(it)
            }
            // הגרף הנבחר (כולל "סה"כ הכנסות") מתרענן דרך ה-observer של netWorthChartPoints,
            // שנקרא מחדש בכל שינוי עסקה — אין צורך לרנדר כאן שוב
        }

        binding.fabAddAsset.setOnClickListener {
            it.hapticTap()
            showAddChooser()
        }

        // "הצג הכל" — הדשבורד מראה חמש עסקאות אחרונות; הטבלה המלאה היא ה-Screener
        binding.tvTransactionsViewAll.setOnClickListener {
            it.hapticTap()
            startActivity(Intent(this, ScreenerActivity::class.java))
        }
    }

    private var errorSnackbar: com.google.android.material.snackbar.Snackbar? = null

    private fun showPortfolioError() {
        if (errorSnackbar?.isShown == true) return
        errorSnackbar = com.google.android.material.snackbar.Snackbar.make(
            binding.drawerLayout,
            R.string.error_load_failed,
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.action_retry) { viewModel.retryPortfolio() }
        errorSnackbar?.show()
    }

    private fun dismissPortfolioError() {
        errorSnackbar?.dismiss()
        errorSnackbar = null
    }

    // בורר הוספה ממסך הבית: גיליון עם שני כרטיסים ברורים —
    // עסקה (תזרים: הכנסה/הוצאה שמשנה את המזומן) מול נכס/התחייבות (מאזן: משהו שמחזיקים לאורך זמן).
    // כל כרטיס כולל הסבר ודוגמאות כדי שיהיה חד־משמעי מה נכנס לאן.
    private fun showAddChooser() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottomsheet_add_chooser, null)
        sheet.setContentView(view)
        view.findViewById<android.view.View>(R.id.cardTransaction).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        view.findViewById<android.view.View>(R.id.cardAsset).setOnClickListener {
            sheet.dismiss()
            // נכס/התחייבות עובר קודם דרך בורר הקטגוריות המקובץ
            startActivity(Intent(this, SelectCategoryActivity::class.java))
        }
        sheet.show()
    }

    override fun onResume() {
        super.onResume()
        // מצב ההסתרה עשוי להשתנות ממסך ההגדרות (מתג "הסתרת סכומים") — קוראים מחדש בכל חזרה.
        // גם מטבע התצוגה עשוי להשתנות שם; הרענון למטה גורם לרינדור מחדש עם הפורמטר העדכני.
        balanceHidden = prefs.getBoolean(KEY_BALANCE_HIDDEN, false)
        renderNetWorth()
        // רענון מחירי מניות חיים בכל חזרה למסך, כדי שהשווי הנקי ישקף את מצב השוק העדכני
        viewModel.refreshLivePrices()
    }

    // ---------- Toolbar + Drawer ----------

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.nav_open, R.string.nav_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_screener -> {
                    startActivity(Intent(this, ScreenerActivity::class.java))
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_assets -> {
                    startActivity(Intent(this, PortfolioActivity::class.java))
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_deepdive -> {
                    binding.drawerLayout.closeDrawers()
                    showDeepDiveCategoryPicker()
                    true
                }
                R.id.nav_spaces -> {
                    startActivity(Intent(this, com.shai.capitall.ui.spaces.SpacesActivity::class.java))
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, com.shai.capitall.ui.settings.SettingsActivity::class.java))
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_language -> {
                    showLanguagePickerDialog()
                    true
                }
                R.id.nav_sign_out -> {
                    signOut()
                    true
                }
                else -> false
            }
        }
    }

    private fun populateDrawerHeader() {
        val user = FirebaseAuth.getInstance().currentUser
        val headerView = binding.navView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.tvUserName).text = user?.displayName ?: "Unknown"
        headerView.findViewById<TextView>(R.id.tvUserEmail).text = user?.email ?: ""
    }

    private fun showLanguagePickerDialog() {
        val languages = arrayOf(getString(R.string.language_english), getString(R.string.language_hebrew))
        val codes = arrayOf(LanguageManager.LANG_ENGLISH, LanguageManager.LANG_HEBREW)
        val currentIndex = codes.indexOf(LanguageManager.getSavedLanguage(this)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.language_picker_title)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                LanguageManager.setLanguage(this, codes[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun showDeepDiveCategoryPicker() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            // רק קטגוריות שיש בהן לפחות עסקת הוצאה אחת — אחרת מסך הניתוח ייפתח ריק
            val categoriesWithExpenses = com.shai.capitall.di.ServiceLocator.transactionRepository
                .observeTransactions(userId)
                .first()
                .filter { it.amount < 0 }
                .map { it.category }
                .toSet()

            // מניות נשמרות כנכסים (לא כעסקאות) — מוסיפים את קטגוריית המניות לבורר אם יש אחזקות
            val hasStocks = com.shai.capitall.di.ServiceLocator.portfolioRepository
                .observeAssets(userId)
                .first()
                .any { it.type == AssetType.STOCK && !it.symbol.isNullOrBlank() }

            val categoryList = com.shai.capitall.di.ServiceLocator.categoryRepository
                .getCategoriesOnce(userId)
                .filter { it != "Income" && it in categoriesWithExpenses }
                .toMutableList()
            if (hasStocks && STOCKS_CATEGORY_KEY !in categoryList) {
                categoryList.add(STOCKS_CATEGORY_KEY)
            }
            val categories = categoryList.toTypedArray()

            if (categories.isEmpty()) {
                Toast.makeText(this@DashboardActivity, getString(R.string.deepdive_empty_state), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val displayLabels = categories.map { CategoryCatalog.labelFor(this@DashboardActivity, it) }.toTypedArray()

            AlertDialog.Builder(this@DashboardActivity)
                .setTitle(R.string.deepdive_choose_category)
                .setItems(displayLabels) { _, which ->
                    val intent = Intent(this@DashboardActivity, DeepDiveActivity::class.java)
                    intent.putExtra(DeepDiveActivity.EXTRA_CATEGORY, categories[which])
                    startActivity(intent)
                }
                .show()
        }
    }

    // ---------- Ticker ----------

    private fun setupTicker() {
        binding.recyclerViewTicker.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    // טיקר מבוסס נתונים אמיתיים: יתרה נוכחית (שווי נקי), הוצאות החודש, ותחזית יתרה לסוף החודש
    private fun renderTicker(state: DashboardUiState) {
        val format = CurrencyConverter.ilsFormatter()
        val monthExpenses = viewModel.currentMonthExpenses()
        val projected = state.netWorth + state.recurringMonthlyIncome - state.recurringMonthlyPayments
        val metrics = listOf(
            TickerMetric(getString(R.string.ticker_current_balance), format.format(state.netWorth), "—", R.color.white),
            TickerMetric(getString(R.string.ticker_month_expenses), format.format(monthExpenses), "▼", R.color.red_negative),
            TickerMetric(getString(R.string.ticker_projected_balance), format.format(projected), "▲", R.color.green_positive)
        )
        binding.recyclerViewTicker.adapter = TickerAdapter(metrics)
    }

    // ---------- Search ----------

    private fun setupSearchBar() {
        // שורת החיפוש בדשבורד פותחת את מסך החיפוש (החיפוש עצמו קורה שם)
        binding.etSearch.isFocusable = false
        binding.etSearch.isClickable = true
        binding.etSearch.setOnClickListener {
            startActivity(Intent(this, com.shai.capitall.ui.search.SearchActivity::class.java))
        }
    }

    // ---------- KPI Cards ----------

    private fun setupKpiCards() {
        binding.recyclerViewKpi.layoutManager = GridLayoutManager(this, 2)
        updateKpiCards(DashboardUiState())
    }

    private fun updateKpiCards(state: DashboardUiState) {
        val format = CurrencyConverter.ilsFormatter()
        val income = viewModel.currentMonthIncome()
        val expenses = viewModel.currentMonthExpenses()
        val savingsRate = if (income > 0) (((income - expenses) / income) * 100).toInt() else 0

        val cards = listOf(
            KpiCard(getString(R.string.kpi_liquid_net_worth), format.format(state.netWorth), R.color.white),
            KpiCard(getString(R.string.kpi_monthly_income), "+${format.format(income)}", R.color.green_positive),
            KpiCard(getString(R.string.kpi_monthly_expenses), "-${format.format(expenses)}", R.color.red_negative),
            KpiCard(getString(R.string.kpi_savings_rate), getString(R.string.kpi_savings_rate_value, savingsRate), R.color.gold_accent),
            KpiCard(getString(R.string.kpi_recurring_income), "+${format.format(state.recurringMonthlyIncome)}", R.color.green_positive),
            KpiCard(getString(R.string.kpi_recurring_payments), "-${format.format(state.recurringMonthlyPayments)}", R.color.red_negative)
        )
        kpiAdapter = KpiAdapter(cards)
        binding.recyclerViewKpi.adapter = kpiAdapter
        binding.recyclerViewKpi.playRiseAnimation()
    }

    // ---------- Cash Flow Bar Chart ----------

    private fun setupCashFlowChart() {
        renderCashFlowChart()
    }

    private fun renderCashFlowChart() {
        val months = viewModel.cashFlowLastSixMonths()

        val incomeEntries = months.mapIndexed { index, month -> BarEntry(index.toFloat(), month.income) }
        val expenseEntries = months.mapIndexed { index, month -> BarEntry(index.toFloat(), month.expenses) }

        val incomeSet = BarDataSet(incomeEntries, "Income").apply {
            color = getColor(R.color.green_positive)
            setDrawValues(false)
        }
        val expenseSet = BarDataSet(expenseEntries, "Expenses").apply {
            color = getColor(R.color.red_negative)
            setDrawValues(false)
        }

        val data = BarData(incomeSet, expenseSet)
        data.barWidth = 0.35f

        binding.cashFlowChart.apply {
            this.data = data
            setNoDataTextColor(getColor(R.color.gray_light))
            description.isEnabled = false
            legend.textColor = getColor(R.color.white)
            axisLeft.textColor = getColor(R.color.gray_light)
            axisRight.isEnabled = false
            setFitBars(true)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(months.map { it.label })
                position = XAxis.XAxisPosition.BOTTOM
                textColor = getColor(R.color.white)
                setDrawGridLines(false)
                granularity = 1f
                setCenterAxisLabels(true)
                axisMinimum = 0f
                axisMaximum = 0f + months.size
            }

            groupBars(0f, 0.2f, 0.05f)
            setTouchEnabled(true)
            setPinchZoom(false)
            animateY(600)
            invalidate()
        }
    }

    // ---------- Donut Chart ----------

    private fun setupDonutChart() {
        binding.donutChart.apply {
            setNoDataTextColor(getColor(R.color.gray_light))
            setNoDataText(getString(R.string.dashboard_chart_no_data))
            description.isEnabled = false
            legend.isEnabled = false
            holeRadius = 65f
            transparentCircleRadius = 68f
            setHoleColor(getColor(R.color.navy_card))
            setCenterTextColor(getColor(R.color.white))
            setCenterTextSize(12f)
            setEntryLabelColor(Color.TRANSPARENT)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val pieEntry = e as? PieEntry ?: return
                    val format = CurrencyConverter.ilsFormatter()
                    Toast.makeText(
                        this@DashboardActivity,
                        "${pieEntry.label}: ${format.format(pieEntry.value)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onNothingSelected() = Unit
            })
        }

        binding.recyclerViewLegend.layoutManager = LinearLayoutManager(this)
    }

    private fun renderDonutChart(state: DashboardUiState) {
        val format = CurrencyConverter.ilsFormatter()
        val grouped = state.assets
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.value } }
            .filterValues { it > 0 }

        val categories = grouped.entries
            .map { (key, amount) ->
                CategoryAmount(
                    label = CategoryCatalog.labelFor(this, key),
                    amount = amount.toFloat(),
                    colorHex = CategoryCatalog.colorFor(key)
                )
            }
            .toMutableList()

        // פרוסת "מזומן" = היתרה המצטברת מהעסקאות (מה שנספר בשווי הנקי מעבר לנכסים הרשומים)
        val cash = state.totalAssets - state.assets.sumOf { it.value }
        if (cash > 0) {
            categories += CategoryAmount(
                label = getString(R.string.dashboard_cash_label),
                amount = cash.toFloat(),
                colorHex = "#27AE60"
            )
        }

        if (categories.isEmpty()) {
            binding.donutChart.clear()
            binding.recyclerViewLegend.adapter = LegendAdapter(emptyList())
            return
        }

        categories.sortByDescending { it.amount }

        val entries = categories.map { PieEntry(it.amount, it.label) }
        val colors = categories.map { Color.parseColor(it.colorHex) }

        binding.donutChart.apply {
            data = PieData(PieDataSet(entries, "").apply {
                this.colors = colors
                setDrawValues(false)
            })
            centerText = "${getString(R.string.dashboard_assets_label)}\n${format.format(categories.sumOf { it.amount.toDouble() })}"
            animateY(600)
            invalidate()
        }

        binding.recyclerViewLegend.adapter = LegendAdapter(categories)
    }

    // ---------- Net Worth Chart (real data) + category selector ----------

    private fun setupNetWorthChart() {
        currentChartSelection = getString(R.string.dashboard_chart_net_worth)
        binding.chipChartView.text = currentChartSelection
        binding.chipTimeRange.text = getString(timeRangeOptions[currentTimeRangeIndex].labelRes)

        binding.netWorthChart.apply {
            setNoDataText(getString(R.string.dashboard_chart_no_data))
            setNoDataTextColor(getColor(R.color.gray_light))
        }

        binding.chipChartView.setOnClickListener {
            showChartSelectorDialog()
        }

        binding.chipTimeRange.setOnClickListener {
            showTimeRangeSelectorDialog()
        }

        viewModel.netWorthChartPoints.observe(this) { points ->
            val categoryKey = currentChartCategoryKey
            if (categoryKey == null) {
                renderChart(points, isNetWorth = true)
            } else {
                // עדכון גם כשמוצגת קטגוריה עם overlay של שווי נקי, כדי שקו הייחוס יתעדכן בזמן אמת
                renderChart(categoryChartPoints(categoryKey), isNetWorth = false)
            }
        }

        viewModel.stockPortfolioChartPoints.observe(this) { points ->
            if (currentChartCategoryKey == STOCKS_CATEGORY_KEY) {
                renderChart(points, isNetWorth = false)
            }
        }
    }

    // לקטגוריית המניות מציג את מגמת שווי-השוק החיה של התיק (מ-Yahoo); "סה"כ הכנסות" מכל העסקאות; לשאר — לפי קטגוריה
    private fun categoryChartPoints(categoryKey: String): List<ChartPoint> =
        when (categoryKey) {
            STOCKS_CATEGORY_KEY -> viewModel.stockPortfolioChartPoints.value ?: emptyList()
            TOTAL_INCOME_KEY -> viewModel.getTotalIncomeChartPoints()
            else -> viewModel.getCategoryChartPoints(categoryKey)
        }

    // מרנדר מחדש את הגרף לפי הבחירה הנוכחית (שווי נקי / סה"כ הכנסות / קטגוריה)
    private fun renderCurrentChart() {
        val key = currentChartCategoryKey
        if (key == null) {
            renderChart(viewModel.netWorthChartPoints.value ?: emptyList(), isNetWorth = true)
        } else {
            renderChart(categoryChartPoints(key), isNetWorth = false)
        }
    }

    private fun showChartSelectorDialog() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            // הבורר מציג רק קטגוריות שבאמת קיימות אצל המשתמש בנתונים (עסקאות/נכסים/התחייבויות
            // שהוסיף), לא את כל הקטלוג. כך אין קטגוריות ריקות, ואין "שאריות" של מפתחות ישנים
            // שירדו מדפי ההוספה.
            val transactions = com.shai.capitall.di.ServiceLocator.transactionRepository.observeTransactions(userId).first()
            val assets = com.shai.capitall.di.ServiceLocator.portfolioRepository.observeAssets(userId).first()
            val liabilities = com.shai.capitall.di.ServiceLocator.portfolioRepository.observeLiabilities(userId).first()
            val present = buildSet {
                transactions.forEach { add(it.category) }
                assets.forEach { add(it.category) }
                liabilities.forEach { add(it.category) }
            }

            // מזהה קטגוריות מותאמות אישית תקפות (מהקטלוג של המשתמש) — כדי לסנן מפתחות ישנים
            // שאינם בקטלוג וגם אינם קטגוריה מותאמת אישית קיימת (למשל "Restaurants" מנתונים ישנים)
            val validCustom = com.shai.capitall.di.ServiceLocator.categoryRepository.getCategoriesOnce(userId)
                .filter { CategoryCatalog.byKey(it) == null }
                .toSet()

            // סדר לוגי לפי הקטלוג (הכנסה → הוצאה → נכסים → התחייבויות), רק לקטגוריות עם נתונים;
            // אחריהן קטגוריות מותאמות אישית תקפות שיש בהן נתונים.
            val catalogOrdered = CategoryCatalog.all.map { it.key }.filter { it in present }
            val customOrdered = present.filter { it.isNotBlank() && it in validCustom }
            val categories = (catalogOrdered + customOrdered).distinct()

            // שתי אפשרויות מובנות בראש: שווי נקי + סה"כ הכנסות, ואז הקטגוריות
            val fixedKeys = listOf<String?>(null, TOTAL_INCOME_KEY)
            val fixedLabels = listOf(
                getString(R.string.dashboard_chart_net_worth),
                getString(R.string.dashboard_chart_total_income)
            )
            val allKeys = fixedKeys + categories
            val displayOptions = fixedLabels + categories.map { CategoryCatalog.labelFor(this@DashboardActivity, it) }

            AlertDialog.Builder(this@DashboardActivity)
                .setTitle(R.string.dashboard_chart_select_title)
                .setItems(displayOptions.toTypedArray()) { _, which ->
                    currentChartSelection = displayOptions[which]
                    binding.chipChartView.text = currentChartSelection
                    currentChartCategoryKey = allKeys[which]
                    renderCurrentChart()
                }
                .show()
        }
    }

    // מקצר סכומים לתצוגת ציר: ₪1.2M / ₪900K / ₪450
    private fun abbreviateShekel(value: Float): String {
        val a = abs(value)
        return when {
            a >= 1_000_000 -> "₪%.1fM".format(Locale.US, value / 1_000_000)
            a >= 1_000 -> "₪%.0fK".format(Locale.US, value / 1_000)
            else -> "₪%.0f".format(Locale.US, value)
        }
    }

    // מסנן את הנקודות לחלון הזמן שנבחר (יום/שבוע/חודש/שנה) ואז מקבץ לגרנולריות תצוגה.
    // מיושם באופן זהה על הסדרה הראשית ועל קו השווי הנקי, כך ששניהם מוצגים לאותו טווח.
    private fun bucketByRange(points: List<ChartPoint>): List<ChartPoint> {
        if (points.isEmpty()) return points
        val option = timeRangeOptions[currentTimeRangeIndex]

        // 1) חיתוך לחלון הזמן — נמדד אחורה מהנקודה האחרונה בסדרה (עקבי לכל הסדרות שמוצגות יחד)
        val windowed = option.windowMillis?.let { w ->
            val anchor = points.maxOf { it.timestamp }
            val cutoff = anchor - w
            points.filter { it.timestamp >= cutoff }
        } ?: points
        // אם החלון דליל מדי (למשל נתונים חודשיים מול חלון של יום) — נשמור לפחות את 2 הנקודות האחרונות
        // כדי שהקו תמיד יצויר, במקום גרף ריק/שבור
        val effective = if (windowed.size >= 2) windowed
        else points.sortedBy { it.timestamp }.takeLast(2)

        val bucket = option.bucket
        if (bucket == Bucket.NONE || effective.isEmpty()) return effective

        val cal = Calendar.getInstance()
        val labelFormat = when (bucket) {
            Bucket.DAY -> SimpleDateFormat("dd/MM", Locale.getDefault())
            Bucket.WEEK -> SimpleDateFormat("dd/MM", Locale.getDefault())
            Bucket.MONTH -> SimpleDateFormat("MM/yy", Locale.getDefault())
            Bucket.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault())
            Bucket.NONE -> SimpleDateFormat("dd/MM", Locale.getDefault())
        }

        fun bucketStart(ts: Long): Long {
            cal.timeInMillis = ts
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            when (bucket) {
                Bucket.WEEK -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                Bucket.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
                Bucket.YEAR -> cal.set(Calendar.DAY_OF_YEAR, 1)
                else -> Unit
            }
            return cal.timeInMillis
        }

        return effective
            .groupBy { bucketStart(it.timestamp) }
            .toSortedMap()
            .map { (start, group) ->
                val avg = group.map { it.value }.average().toFloat()
                ChartPoint(labelFormat.format(Date(start)), avg, start)
            }
    }

    private fun showTimeRangeSelectorDialog() {
        val options = timeRangeOptions.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.dashboard_chart_range_select_title)
            .setItems(options) { _, which ->
                currentTimeRangeIndex = which
                binding.chipTimeRange.text = options[which]
                renderChart(lastRenderedRawPoints, lastRenderedIsNetWorth)
            }
            .show()
    }

    private class AlignedSeries(
        val labels: List<String>,
        val timestamps: List<Long>,
        val primary: List<Entry>,
        val reference: List<Entry>
    )

    // מיישר שתי סדרות (קטגוריה + שווי נקי) לציר-זמן משותף = איחוד התאריכים שלהן, ממוין.
    // כל סדרה מקבלת ערך בכל תאריך ע"י השלמה קדימה (הערך האחרון הידוע עד אותו תאריך),
    // כך ששתי הסדרות משתרעות על מלוא רוחב הגרף ומתיישרות לפי זמן אמיתי — קו השווי הנקי
    // שומר על צורתו המלאה במקום להצטמצם לנקודות של קטגוריה דלילה.
    private fun alignSeries(primary: List<ChartPoint>, reference: List<ChartPoint>): AlignedSeries {
        val timestamps = com.shai.capitall.util.SeriesAligner.unionTimestamps(
            primary.map { it.timestamp }, reference.map { it.timestamp }
        )
        val labelByTs = (reference + primary).associate { it.timestamp to it.label }
        val labels = timestamps.map { labelByTs[it] ?: "" }

        val primaryMap = com.shai.capitall.util.SeriesAligner.forwardFill(primary.map { it.timestamp to it.value }, timestamps)
        val refMap = com.shai.capitall.util.SeriesAligner.forwardFill(reference.map { it.timestamp to it.value }, timestamps)
        val primaryEntries = primaryMap.entries.sortedBy { it.key }.map { Entry(it.key.toFloat(), it.value) }
        val refEntries = refMap.entries.sortedBy { it.key }.map { Entry(it.key.toFloat(), it.value) }
        return AlignedSeries(labels, timestamps, primaryEntries, refEntries)
    }

    private fun renderChart(rawPoints: List<ChartPoint>, isNetWorth: Boolean) {
        lastRenderedRawPoints = rawPoints
        lastRenderedIsNetWorth = isNetWorth

        val points = bucketByRange(rawPoints)
        if (points.isEmpty()) {
            binding.netWorthChart.clear()
            return
        }

        val gold = getColor(R.color.gold_accent)
        // קו-ייחוס השווי הנקי מקבל צבע מובחן משלו — אחרת שתי הסדרות כחולות ולא ניתנות להבחנה
        val benchmarkColor = getColor(R.color.chart_benchmark)
        // בבחירת קטגוריה מוסיפים את השווי הנקי כקו-ייחוס קבוע (benchmark) שנשאר תמיד על הגרף
        val overlayNetWorth = !isNetWorth

        val primaryColor = if (isNetWorth) gold else getColor(R.color.chart_category)
        val label = if (isNetWorth) getString(R.string.dashboard_chart_net_worth) else currentChartSelection

        // בהשוואת קטגוריה מיישרים את הקטגוריה ואת השווי הנקי לציר-זמן משותף, כדי שקו השווי הנקי
        // ישמור על צורתו המלאה. במצב שווי-נקי בלבד — ציר פשוט מהנקודות עצמן.
        val axisLabels: List<String>
        val axisTimestamps: List<Long>
        val primaryEntries: List<Entry>
        var nwEntries: List<Entry>? = null

        val nwPoints = if (overlayNetWorth) bucketByRange(viewModel.netWorthChartPoints.value ?: emptyList()) else emptyList()
        if (overlayNetWorth && nwPoints.isNotEmpty()) {
            val aligned = alignSeries(points, nwPoints)
            axisLabels = aligned.labels
            axisTimestamps = aligned.timestamps
            primaryEntries = aligned.primary
            nwEntries = aligned.reference
        } else {
            axisLabels = points.map { it.label }
            axisTimestamps = points.map { it.timestamp }
            primaryEntries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.value) }
        }

        val dataSets = mutableListOf<ILineDataSet>()

        // הקו הראשי (שווי נקי או הקטגוריה) — ציר שמאל, עם מילוי גרדיאנט
        dataSets += LineDataSet(primaryEntries, label).apply {
            color = primaryColor
            axisDependency = YAxis.AxisDependency.LEFT
            lineWidth = 2.4f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(90, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                    Color.TRANSPARENT
                )
            )
            highLightColor = gold
            setDrawHorizontalHighlightIndicator(false)
        }

        // קו ייחוס השווי הנקי — ציר ימין נפרד (סקאלה משלו) + מקווקו, על ציר-הזמן המשותף
        nwEntries?.let { refEntries ->
            dataSets += LineDataSet(refEntries, getString(R.string.dashboard_chart_net_worth)).apply {
                color = benchmarkColor
                axisDependency = YAxis.AxisDependency.RIGHT
                lineWidth = 1.5f
                enableDashedLine(14f, 8f, 0f)
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(false)
                isHighlightEnabled = false
            }
        }

        val shekelFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String = abbreviateShekel(value)
        }

        binding.netWorthChart.apply {
            data = LineData(dataSets)
            description.isEnabled = false

            // מקרא מוצג רק במצב המשולב, ומעוצב כקו (לא ריבוע) כדי לשקף את סגנון הקו בפועל —
            // כולל הקו המקווקו של הבנצ'מרק. יושב מחוץ לאזור הציור כדי לא לכסות נתונים.
            legend.isEnabled = overlayNetWorth
            legend.textColor = getColor(R.color.on_surface_muted)
            legend.form = Legend.LegendForm.LINE
            legend.formSize = 10f
            legend.formLineWidth = 2f
            legend.textSize = 11f
            legend.xEntrySpace = 14f
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)

            axisLeft.textColor = getColor(R.color.gray_light)
            axisLeft.setDrawAxisLine(false)
            // קווי רשת אופקיים מקווקווים ועדינים — קריאת ערכים נוחה בלי רעש ויזואלי
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = getColor(R.color.hairline)
            axisLeft.enableGridDashedLine(10f, 8f, 0f)
            axisLeft.setLabelCount(5, false)
            axisLeft.spaceTop = 24f
            axisLeft.spaceBottom = 24f
            axisLeft.valueFormatter = shekelFormatter

            // ציר ימין = סקאלת השווי הנקי (מופעל רק כשמוצג קו הייחוס).
            // צבוע בצבע הבנצ'מרק כדי שיהיה חד-משמעי איזה ציר שייך לאיזה קו.
            axisRight.isEnabled = overlayNetWorth
            axisRight.textColor = benchmarkColor
            axisRight.setDrawAxisLine(false)
            axisRight.setDrawGridLines(false)
            axisRight.setLabelCount(5, false)
            axisRight.spaceTop = 24f
            axisRight.spaceBottom = 24f
            axisRight.valueFormatter = shekelFormatter

            setDrawGridBackground(false)
            // שתי אצבעות שמורות למצב ההשוואה — מבטלים זום/צביטה כדי שלא יתנגשו במחווה
            isDoubleTapToZoomEnabled = false
            setPinchZoom(false)
            setScaleEnabled(false)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(axisLabels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = getColor(R.color.gray_light)
                setDrawAxisLine(false)
                setDrawGridLines(false)
                granularity = 1f
                // מצמצם את מספר תוויות הזמן כדי שהתצוגה תישאר נקייה בכל טווח (יום/שבוע/חודש/שנה)
                setLabelCount(4, false)
                setAvoidFirstLastClipping(true)
            }

            // תווית צפה בסגנון TradingView: בלחיצה/גרירה מופיע קו אנכי + תיבה עם הערך והתאריך בראש הגרף
            val format = CurrencyConverter.ilsFormatter()
            val markerFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val markerLabels = axisTimestamps.map { markerFormat.format(Date(it)) }
            marker = ChartMarkerView(this@DashboardActivity, markerLabels) { format.format(it) }

            // מצב השוואה בשתי אצבעות: איפוס בכל רינדור (האינדקסים משתנים עם הנתונים)
            // והזרקת אותם פורמטי מטבע/תאריך של התווית הצפה — עקביות מלאה
            clearCompare()
            compareValueFormatter = { v -> format.format(v.toDouble()) }
            compareDateFormatter = { i -> markerLabels.getOrNull(i) ?: "" }

            animateX(600)
            invalidate()
        }
    }

    // ---------- הסתרת השווי הנקי ----------

    private fun setupBalanceToggle() {
        balanceHidden = prefs.getBoolean(KEY_BALANCE_HIDDEN, false)
        renderNetWorth()
        binding.btnToggleBalance.setOnClickListener {
            it.hapticTap()
            balanceHidden = !balanceHidden
            prefs.edit().putBoolean(KEY_BALANCE_HIDDEN, balanceHidden).apply()
            renderNetWorth()
        }
    }

    private fun renderNetWorth() {
        if (balanceHidden) {
            binding.tvNetWorthValue.text = getString(R.string.dashboard_balance_hidden)
        } else {
            // ספירה מונפשת אל הערך החדש — הערך הפיננסי המרכזי "מתגלגל" במקום לקפוץ
            val format = CurrencyConverter.ilsFormatter()
            binding.tvNetWorthValue.setValueAnimated(lastNetWorth) { format.format(it) }
        }
        binding.btnToggleBalance.setImageResource(
            if (balanceHidden) R.drawable.ic_eye_off else R.drawable.ic_eye
        )
    }

    // ---------- Bottom Widgets ----------

    private fun setupBottomWidgets() {
        binding.recyclerViewTransactions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewInsights.layoutManager = LinearLayoutManager(this)
        // כרטיס העסקאות האחרונות מקצר למסך המסנן — כל העסקאות וההוצאות במקום אחד
        binding.cardTransactions.setOnClickListener {
            startActivity(Intent(this, ScreenerActivity::class.java))
        }
        renderBottomWidgets()
    }

    private fun renderBottomWidgets() {
        binding.recyclerViewTransactions.adapter =
            TransactionWidgetAdapter(viewModel.recentTransactions())
        binding.recyclerViewInsights.adapter =
            InsightAdapter(computeInsights())
        // כניסה מדורגת — הרשימות "נבנות" לתוך המסך במקום להופיע בבת אחת
        binding.recyclerViewTransactions.playRiseAnimation()
        binding.recyclerViewInsights.playRiseAnimation()
    }

    // תובנות אמיתיות, מחושבות מהעסקאות של המשתמש (ריק עד שיהיו נתונים)
    private fun computeInsights(): List<String> {
        val format = CurrencyConverter.ilsFormatter()
        val result = mutableListOf<String>()

        viewModel.topExpenseCategoryThisMonth()?.let { (key, amount) ->
            result += getString(
                R.string.insight_top_expense,
                CategoryCatalog.labelFor(this, key),
                format.format(amount)
            )
        }

        val income = viewModel.currentMonthIncome()
        if (income > 0) {
            val expenses = viewModel.currentMonthExpenses()
            val rate = (((income - expenses) / income) * 100).toInt()
            result += if (rate >= 0) {
                getString(R.string.insight_savings, rate)
            } else {
                getString(R.string.insight_overspending)
            }
        }

        val recurring = viewModel.recurringTransactionsCount()
        if (recurring > 0) {
            result += getString(R.string.insight_recurring, recurring)
        }
        return result
    }

    // ---------- Sign Out ----------

    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.signOut().addOnCompleteListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}