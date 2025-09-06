package systems.choochoo.transit_data_archivers.njt.utils

import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.module.kotlin.readValue
import systems.choochoo.transit_data_archivers.njt.model.ErrorMessage
import java.io.IOException

internal fun parseErrorMessage(om: ObjectMapper, errorBody: ByteArray?): String? = errorBody?.let {
    try {
        om.readValue<ErrorMessage>(it)
    } catch (_: RuntimeJsonMappingException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: StreamReadException) {
        null
    } catch (_: DatabindException) {
        null
    }?.errorMessage
}