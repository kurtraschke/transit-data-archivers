package systems.choochoo.transit_data_archivers.njt.services

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import okhttp3.ResponseBody
import retrofit2.Response
import systems.choochoo.transit_data_archivers.njt.model.AuthenticateUserResponse
import java.util.concurrent.CompletableFuture

internal interface RealtimeService {
    fun authenticateUser(username: String, password: String): CompletableFuture<Response<AuthenticateUserResponse>>

    fun getGTFS(token: String): CompletableFuture<ResponseBody>

    fun getAlerts(token: String): CompletableFuture<FeedMessage>

    fun getTripUpdates(token: String): CompletableFuture<FeedMessage>

    fun getVehiclePositions(token: String): CompletableFuture<FeedMessage>
}