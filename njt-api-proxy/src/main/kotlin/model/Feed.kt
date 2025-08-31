package systems.choochoo.transit_data_archivers.njt.model

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import retrofit2.Call
import systems.choochoo.transit_data_archivers.njt.services.RealtimeService

internal enum class Feed(val requestFunction: (RealtimeService, String) -> Call<FeedMessage>) {
    ALERTS(RealtimeService::getAlerts),
    TRIP_UPDATES(RealtimeService::getTripUpdates),
    VEHICLE_POSITIONS(RealtimeService::getVehiclePositions)
}