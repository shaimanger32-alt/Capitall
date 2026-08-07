package com.shai.capitall

import com.shai.capitall.util.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyConverterTest {

    @Test
    fun `default rate constant`() {
        assertEquals(3.7, CurrencyConverter.DEFAULT_USD_ILS, 0.0)
    }

    @Test
    fun `updateRate then convert usd to ils`() {
        CurrencyConverter.updateRate(4.0)
        assertEquals(4.0, CurrencyConverter.usdToIls, 0.0)
        assertEquals(40.0, CurrencyConverter.usdToIls(10.0), 0.0001)
    }

    @Test
    fun `updateRate ignores non-positive values`() {
        CurrencyConverter.updateRate(3.9)
        CurrencyConverter.updateRate(0.0)
        CurrencyConverter.updateRate(-2.0)
        assertEquals(3.9, CurrencyConverter.usdToIls, 0.0)
    }

    @Test
    fun `usd formatter includes dollar sign`() {
        assertTrue(CurrencyConverter.formatUsd(10.0).contains("$"))
    }

    @Test
    fun `ils formatter is non-blank`() {
        assertTrue(CurrencyConverter.formatIls(1234.0).isNotBlank())
    }
}
