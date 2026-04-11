package systems.choochoo.transit_data_archivers.njt.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import systems.choochoo.transit_data_archivers.common.utils.EpochSecondInstant
import systems.choochoo.transit_data_archivers.njt.utils.TOKEN_LIFETIME
import kotlin.time.Clock
import kotlin.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Token(
    val token: String,
    @param:JsonSerialize(using = EpochSecondInstant.Serializer::class)
    @param:JsonDeserialize(using = EpochSecondInstant.Deserializer::class)
    val whenObtained: Instant,
) {
    @get:JsonIgnore
    val presumedExpiration: Instant
        get() = whenObtained + TOKEN_LIFETIME

    @get:JsonIgnore
    val isValid: Boolean
        get() = presumedExpiration >= Clock.System.now()
}