package systems.choochoo.transit_data_archivers.trackernet.services

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import uk.co.lul.trackernet.predictiondetail.PredictionDetail
import uk.co.lul.trackernet.predictionsummary.PredictionSummary

internal interface TrackernetService {
    @GET("PredictionSummary/{lineCode}")
    fun getPredictionSummary(@Path("lineCode") lineCode: String): Call<PredictionSummary>

    @GET("PredictionDetailed/{lineCode}/{stationCode}")
    fun getPredictionDetail(
        @Path("lineCode") lineCode: String,
        @Path("stationCode") stationCode: String
    ): Call<PredictionDetail>
}