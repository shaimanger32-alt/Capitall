package com.shai.capitall

import com.google.gson.Gson
import com.shai.capitall.data.remote.BoiSdmxResponse
import com.shai.capitall.util.BoiSdmxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoiSdmxParserTest {

    private fun parse(json: String): BoiSdmxResponse =
        Gson().fromJson(json, BoiSdmxResponse::class.java)

    /** תשובה בפורמט SDMX-JSON 2.0 (structures כמערך) — כפי שמחזיר Fusion Edge של בנק ישראל. */
    private val sample = """
    {
      "data": {
        "dataSets": [
          {
            "series": {
              "0:0:0:0:0:0": {
                "observations": {
                  "0": ["3.61", 0],
                  "1": ["3.68", 0],
                  "2": ["3.72", 0]
                }
              }
            }
          }
        ],
        "structures": [
          {
            "dimensions": {
              "observation": [
                {
                  "id": "TIME_PERIOD",
                  "values": [
                    {"id": "2026-07-30"},
                    {"id": "2026-07-31"},
                    {"id": "2026-08-03"}
                  ]
                }
              ]
            }
          }
        ]
      }
    }
    """.trimIndent()

    @Test
    fun `parses all observations in chronological order`() {
        val points = BoiSdmxParser.parseSeries(parse(sample))
        assertEquals(3, points.size)
        assertEquals(listOf(3.61, 3.68, 3.72), points.map { it.rate })
        assertTrue("must be sorted by time", points.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun `latest rate is the newest observation`() {
        assertEquals(3.72, BoiSdmxParser.parseLatestRate(parse(sample))!!, 0.0001)
    }

    @Test
    fun `supports sdmx-json 1_0 shape with single structure object`() {
        val v1 = """
        {"data":{"dataSets":[{"series":{"0:0":{"observations":{"0":["3.55",0]}}}}],
         "structure":{"dimensions":{"observation":[
           {"id":"TIME_PERIOD","values":[{"id":"2026-08-03"}]}]}}}}
        """.trimIndent()
        val points = BoiSdmxParser.parseSeries(parse(v1))
        assertEquals(1, points.size)
        assertEquals(3.55, points.first().rate, 0.0001)
    }

    @Test
    fun `skips non-positive and unparsable rates`() {
        val json = """
        {"data":{"dataSets":[{"series":{"0":{"observations":{
          "0":["0",0],"1":["abc",0],"2":["3.9",0]}}}}],
         "structures":[{"dimensions":{"observation":[{"id":"TIME_PERIOD","values":[
          {"id":"2026-08-01"},{"id":"2026-08-02"},{"id":"2026-08-03"}]}]}}]}}
        """.trimIndent()
        val points = BoiSdmxParser.parseSeries(parse(json))
        assertEquals(1, points.size)
        assertEquals(3.9, points.first().rate, 0.0001)
    }

    @Test
    fun `empty or malformed response yields no points`() {
        assertTrue(BoiSdmxParser.parseSeries(null).isEmpty())
        assertTrue(BoiSdmxParser.parseSeries(parse("{}")).isEmpty())
        assertNull(BoiSdmxParser.parseLatestRate(parse("""{"data":{}}""")))
    }
}
