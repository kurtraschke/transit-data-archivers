@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.common.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class EpochSecondInstant private constructor() {
    class Serializer : JsonSerializer<Instant>() {
        override fun serialize(
            value: Instant,
            gen: JsonGenerator,
            serializers: SerializerProvider
        ) {
            gen.writeObject(value.epochSeconds)
        }
    }

    class Deserializer : JsonDeserializer<Instant>() {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext
        ): Instant = Instant.fromEpochSeconds(p.longValue)
    }
}

