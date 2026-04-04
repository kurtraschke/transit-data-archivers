package systems.choochoo.transit_data_archivers.common.utils

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import kotlin.time.Instant
import kotlin.time.toJavaInstant

fun Instant.toHttpDateString(): String = RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(this.toJavaInstant())