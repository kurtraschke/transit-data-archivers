package systems.choochoo.transit_data_archivers.common.utils

import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExperimentalTime
fun Instant.toHttpDateString(): String {
    return this.format(httpDateFormat, UtcOffset.ZERO)
}

private val httpDateFormat = DateTimeComponents.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    day()
    chars(" ")
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    year()
    chars(" ")
    hour()
    chars(":")
    minute()
    chars(":")
    second()
    chars(" GMT")
}