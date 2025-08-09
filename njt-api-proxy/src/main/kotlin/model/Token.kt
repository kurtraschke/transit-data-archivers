@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.njt.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import systems.choochoo.transit_data_archivers.common.utils.EpochSecondInstant
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal data class Token(
    val token: String,
    @param:JsonSerialize(using = EpochSecondInstant.Serializer::class)
    @param:JsonDeserialize(using = EpochSecondInstant.Deserializer::class)
    val whenObtained: Instant,
)