package systems.choochoo.transit_data_archivers.njt.services

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import okhttp3.ResponseBody
import retrofit2.Call
import systems.choochoo.transit_data_archivers.njt.model.AuthenticateUserResponse

internal interface RealtimeService {
    fun authenticateUser(username: String, password: String): Call<AuthenticateUserResponse>

    fun getGTFS(token: String): Call<ResponseBody>

    fun getAlerts(token: String): Call<FeedMessage>

    fun getTripUpdates(token: String): Call<FeedMessage>

    fun getVehiclePositions(token: String): Call<FeedMessage>
}