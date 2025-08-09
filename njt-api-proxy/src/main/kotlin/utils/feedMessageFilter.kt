package systems.choochoo.transit_data_archivers.njt.utils

import com.google.transit.realtime.GtfsRealtime.FeedMessage

internal fun filterInvalidEntities(fm: FeedMessage) : FeedMessage {
    val b = fm.toBuilder()

    val validEntities = b.entityList.filter { entity -> entity.isInitialized }.toList()

    b.clearEntity()
    b.addAllEntity(validEntities)

    return b.build()
}