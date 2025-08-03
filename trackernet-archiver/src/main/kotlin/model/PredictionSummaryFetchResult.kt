@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.trackernet.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import systems.choochoo.transit_data_archivers.common.utils.EpochSecondInstant
import uk.co.lul.trackernet.predictionsummary.PredictionSummary
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@JsonNaming(SnakeCaseStrategy::class)
internal data class PredictionSummaryFetchResult(
    @get:JsonSerialize(using = EpochSecondInstant.Serializer::class)
    @set:JsonDeserialize(using = EpochSecondInstant.Deserializer::class)
    var fetchTime: Instant,
    var lineCode: String,
    @get:JsonProperty("prediction_summary_json")
    var predictionSummary: PredictionSummary,
)