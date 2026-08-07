package com.shai.capitall

import com.shai.capitall.data.model.Asset
import com.shai.capitall.data.model.AssetType
import com.shai.capitall.data.model.Liability
import com.shai.capitall.util.AssetValuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetValuationTest {

    private val yearMillis = (365.25 * 24 * 60 * 60 * 1000).toLong()
    private val now = 1_700_000_000_000L

    private fun asset(
        value: Double,
        category: String,
        createdAt: Long = now,
        type: AssetType = AssetType.MANUAL,
        annualRate: Double? = null
    ) = Asset(value = value, category = category, createdAt = createdAt, type = type, annualRate = annualRate)

    private fun liability(
        value: Double,
        category: String,
        createdAt: Long = now,
        termYears: Int? = null,
        annualRate: Double? = null
    ) = Liability(value = value, category = category, createdAt = createdAt, termYears = termYears, annualRate = annualRate)

    @Test
    fun `zero-rate category keeps base value`() {
        val a = asset(1000.0, "other", createdAt = now - 5 * yearMillis)
        assertEquals(1000.0, AssetValuation.projectedAssetValue(a, now), 0.001)
    }

    @Test
    fun `real estate appreciates by category rate over one year`() {
        val a = asset(100_000.0, "real_estate", createdAt = now - yearMillis)
        // 3.5% שנתי
        assertEquals(103_500.0, AssetValuation.projectedAssetValue(a, now), 1.0)
    }

    @Test
    fun `vehicle depreciates`() {
        val a = asset(100_000.0, "vehicle", createdAt = now - yearMillis)
        assertTrue(AssetValuation.projectedAssetValue(a, now) < 100_000.0)
    }

    @Test
    fun `custom annual rate overrides category default`() {
        val a = asset(1000.0, "real_estate", annualRate = 0.10)
        assertEquals(0.10, AssetValuation.effectiveAssetRate(a), 0.0)
    }

    @Test
    fun `asset change percent is null when rate is zero`() {
        assertNull(AssetValuation.assetChangePercent(asset(1000.0, "other")))
    }

    @Test
    fun `amortizing classification`() {
        assertTrue(AssetValuation.isAmortizing("mortgage"))
        assertTrue(AssetValuation.isAmortizing("loan"))
        assertTrue(AssetValuation.isAmortizing("student_loan"))
        assertFalse(AssetValuation.isAmortizing("credit_card"))
    }

    @Test
    fun `non-amortizing liability keeps its value`() {
        val l = liability(5000.0, "credit_card", createdAt = now - 3 * yearMillis)
        assertEquals(5000.0, AssetValuation.projectedLiabilityValue(l, now), 0.001)
    }

    @Test
    fun `mortgage balance equals principal at origination and decreases over time`() {
        val principal = 1_000_000.0
        val l = liability(principal, "mortgage", createdAt = now, termYears = 30)
        // בזמן ההקמה היתרה שווה לקרן
        assertEquals(principal, AssetValuation.projectedLiabilityValue(l, now), 1.0)
        // אחרי 5 שנים היתרה קטנה מהקרן ונשארת חיובית
        val after5y = AssetValuation.projectedLiabilityValue(l, now + 5 * yearMillis)
        assertTrue(after5y < principal)
        assertTrue(after5y > 0.0)
    }

    @Test
    fun `mortgage is fully paid at end of term`() {
        val l = liability(500_000.0, "mortgage", createdAt = now, termYears = 20)
        val atEnd = AssetValuation.projectedLiabilityValue(l, now + 20 * yearMillis)
        assertEquals(0.0, atEnd, 1.0)
    }

    @Test
    fun `amortizing liability without term stays flat`() {
        val l = liability(300_000.0, "mortgage", createdAt = now - yearMillis, termYears = null)
        assertEquals(300_000.0, AssetValuation.projectedLiabilityValue(l, now), 0.001)
    }
}
