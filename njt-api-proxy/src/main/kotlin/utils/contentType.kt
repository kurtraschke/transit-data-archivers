package systems.choochoo.transit_data_archivers.njt.utils

import com.google.common.net.MediaType
import io.javalin.http.Context

internal fun Context.contentType(mt: MediaType): Context {
    return this.contentType(mt.toString())
}