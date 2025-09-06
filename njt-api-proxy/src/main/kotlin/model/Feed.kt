package systems.choochoo.transit_data_archivers.njt.model

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import systems.choochoo.transit_data_archivers.njt.services.RealtimeService
import java.util.concurrent.CompletableFuture

internal enum class Feed(val requestFunction: (RealtimeService, String) -> CompletableFuture<FeedMessage>) {
    ALERTS(RealtimeService::getAlerts),
    TRIP_UPDATES(RealtimeService::getTripUpdates),
    VEHICLE_POSITIONS(RealtimeService::getVehiclePositions)
}