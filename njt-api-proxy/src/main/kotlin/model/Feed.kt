package systems.choochoo.transit_data_archivers.njt.model

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import retrofit2.Call
import systems.choochoo.transit_data_archivers.njt.services.RealtimeService

internal enum class Feed {
    ALERTS {
        override val requestFunction = RealtimeService::getAlerts
    },
    TRIP_UPDATES {
        override val requestFunction = RealtimeService::getTripUpdates
    },
    VEHICLE_POSITIONS {
        override val requestFunction = RealtimeService::getVehiclePositions
    };

    abstract val requestFunction : (RealtimeService, String) -> Call<FeedMessage>
}