@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.trackernet.model

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import uk.co.lul.trackernet.predictionsummary.PredictionSummary
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@JsonNaming(SnakeCaseStrategy::class)
internal data class PredictionSummaryFetchResult(
    var fetchTime: Instant,
    var lineCode: String,
    @get:JsonProperty("prediction_summary_json")
    var predictionSummary: PredictionSummary,
) {
    @JsonGetter("fetch_time")
    fun getFetchTimeEpoch() = fetchTime.epochSeconds
}