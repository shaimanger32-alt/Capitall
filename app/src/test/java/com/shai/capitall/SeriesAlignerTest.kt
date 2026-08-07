package com.shai.capitall

import com.shai.capitall.util.SeriesAligner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesAlignerTest {

    @Test
    fun `union is distinct and sorted`() {
        val union = SeriesAligner.unionTimestamps(listOf(150L, 50L), listOf(100L, 50L))
        assertEquals(listOf(50L, 100L, 150L), union)
    }

    @Test
    fun `reference keeps all points on the shared axis`() {
        val axis = listOf(50L, 100L, 150L)
        val reference = SeriesAligner.forwardFill(listOf(50L to 1f, 100L to 2f, 150L to 3f), axis)
        assertEquals(mapOf(0 to 1f, 1 to 2f, 2 to 3f), reference)
    }

    @Test
    fun `sparse primary forward-fills and drops leading gap`() {
        val axis = listOf(50L, 100L, 150L)
        // הקטגוריה מתחילה רק ב-100 ואין לה נקודה ב-150 → השלמה קדימה של הערך האחרון
        val primary = SeriesAligner.forwardFill(listOf(100L to 5f), axis)
        assertFalse("אינדקס לפני הנקודה הראשונה נשמט", primary.containsKey(0))
        assertEquals(5f, primary[1])
        assertEquals("הערך האחרון מושלם קדימה", 5f, primary[2])
    }

    @Test
    fun `empty series yields empty fill`() {
        assertTrue(SeriesAligner.forwardFill(emptyList(), listOf(1L, 2L)).isEmpty())
    }
}
