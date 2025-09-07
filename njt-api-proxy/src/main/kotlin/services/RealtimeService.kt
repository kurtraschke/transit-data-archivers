package systems.choochoo.transit_data_archivers.njt.services

import okhttp3.ResponseBody
import retrofit2.Call
import systems.choochoo.transit_data_archivers.njt.model.AuthenticateUserResponse
import java.util.concurrent.CompletableFuture

internal interface RealtimeService {
    fun authenticateUser(username: String, password: String): Call<AuthenticateUserResponse>

    fun getGTFS(token: String): CompletableFuture<ResponseBody>

    fun getAlerts(token: String): CompletableFuture<ResponseBody>

    fun getTripUpdates(token: String): CompletableFuture<ResponseBody>

    fun getVehiclePositions(token: String): CompletableFuture<ResponseBody>
}