@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.trackernet.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import systems.choochoo.transit_data_archivers.common.utils.EpochSecondInstant
import uk.co.lul.trackernet.predictiondetail.PredictionDetail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@JsonNaming(SnakeCaseStrategy::class)
internal data class PredictionDetailFetchResult(
    @get:JsonSerialize(using = EpochSecondInstant.Serializer::class)
    @set:JsonDeserialize(using = EpochSecondInstant.Deserializer::class)
    var fetchTime: Instant,
    var lineCode: String,
    var stationCode: String,
    @get:JsonProperty("prediction_details_json")
    val predictionDetail: PredictionDetail,
)