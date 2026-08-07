package com.shai.capitall.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ה-API הרשמי של בנק ישראל לשערי החליפין היציגים (Fusion Edge / SDMX).
 * ללא מפתח וללא הרשמה.
 *
 * דוגמה:
 * .../EXR/1.0/RER_USD_ILS?format=sdmx-json&lastNObservations=1
 *
 * הערה: בנק ישראל מפרסם **שער יציג אחד ליום עסקים** (בסביבות 15:15), ולא שער תוך-יומי.
 * זהו השער הרשמי בישראל — ולכן הנכון להצגת שווי נקי שקלי.
 */
interface BoiFxApi {

    @GET("FusionEdgeServer/sdmx/v2/data/dataflow/BOI.STATISTICS/EXR/1.0/{series}")
    suspend fun series(
        @Path("series") series: String,
        @Query("format") format: String = "sdmx-json",
        @Query("startPeriod") startPeriod: String? = null,
        @Query("lastNObservations") lastNObservations: Int? = null
    ): BoiSdmxResponse

    companion object {
        /** מזהי הסדרות: שער יציג של מטבע מול השקל. */
        const val SERIES_USD_ILS = "RER_USD_ILS"
        const val SERIES_EUR_ILS = "RER_EUR_ILS"
    }
}
