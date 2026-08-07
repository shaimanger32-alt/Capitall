package com.shai.capitall.data.remote

import com.google.gson.annotations.SerializedName

/**
 * מודלים לתשובת SDMX-JSON של בנק ישראל (Fusion Edge).
 *
 * מבנה התשובה: התצפיות (observations) ממופות לפי *אינדקס* ולא לפי תאריך —
 * האינדקס מצביע למיקום ברשימת התאריכים שב-structure. לכן נדרשת הצלבה בין שניהם
 * (ראה [com.shai.capitall.util.BoiSdmxParser]).
 *
 * המבנה נתמך גם בגרסה 1.0 (structure יחיד) וגם 2.0 (structures כמערך).
 */
data class BoiSdmxResponse(
    @SerializedName("data") val data: BoiData? = null
)

data class BoiData(
    @SerializedName("dataSets") val dataSets: List<BoiDataSet>? = null,
    @SerializedName("structures") val structures: List<BoiStructure>? = null,
    @SerializedName("structure") val structure: BoiStructure? = null
)

data class BoiDataSet(
    /** מפתח הסדרה → תצפיות. הערך של כל תצפית הוא מערך שאיברו הראשון הוא השער. */
    @SerializedName("series") val series: Map<String, BoiSeries>? = null,
    /** בתשובות ללא מימדי סדרה, התצפיות עשויות להופיע ישירות. */
    @SerializedName("observations") val observations: Map<String, List<Any?>>? = null
)

data class BoiSeries(
    @SerializedName("observations") val observations: Map<String, List<Any?>>? = null
)

data class BoiStructure(
    @SerializedName("dimensions") val dimensions: BoiDimensions? = null
)

data class BoiDimensions(
    /** מימד התצפית — בפועל TIME_PERIOD, שערכיו הם התאריכים לפי סדר האינדקס. */
    @SerializedName("observation") val observation: List<BoiDimension>? = null
)

data class BoiDimension(
    @SerializedName("id") val id: String? = null,
    @SerializedName("values") val values: List<BoiDimensionValue>? = null
)

data class BoiDimensionValue(
    /** תאריך בפורמט yyyy-MM-dd. */
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null
)
